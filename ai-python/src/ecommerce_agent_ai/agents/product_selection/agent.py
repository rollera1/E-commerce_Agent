import re
import time
from html import unescape
from typing import TypedDict
from uuid import uuid4

import openai
from langgraph.graph import END, START, StateGraph

from ecommerce_agent_ai.agents.product_selection.errors import ProductSelectionAgentError
from ecommerce_agent_ai.agents.product_selection.models import (
    CandidateProduct,
    InventoryStatus,
    ProductSelectionResult,
    ProductSelectionTrace,
    SearchAssessment,
    SearchDecision,
    SelectionOutcome,
    SelectionSearchTrace,
)
from ecommerce_agent_ai.agents.product_selection.port import ProductSelectionReasoner
from ecommerce_agent_ai.agents.product_selection.tools import SearchSourceProductsTool
from ecommerce_agent_ai.product_sources.cj_error import CjApiError
from ecommerce_agent_ai.product_sources.models import SourceProduct
from ecommerce_agent_ai.product_sources.port import ProductSource


class _SelectionState(TypedDict, total=False):
    user_query: str
    limit: int
    search_query: str
    products: list[SourceProduct]
    assessment: SearchAssessment
    steps: list[str]
    searches: list[SelectionSearchTrace]
    search_count: int
    rewrite_count: int
    model_attempts: int
    allow_rewrite: bool


class ProductSelectionGraphAgent:
    """使用显式 LangGraph 节点和条件边执行受限关键词选品。"""

    def __init__(
        self,
        reasoner: ProductSelectionReasoner,
        product_source: ProductSource,
    ) -> None:
        self._reasoner = reasoner
        self._search_tool = SearchSourceProductsTool(product_source)
        self._graph = self._build_graph()

    def select(self, query: str, limit: int = 10) -> ProductSelectionResult:
        normalized_query = query.strip()
        if not normalized_query:
            raise ValueError("query must not be blank")
        if limit < 1 or limit > 100:
            raise ValueError("limit must be between 1 and 100")

        initial_state: _SelectionState = {
            "user_query": normalized_query,
            "limit": limit,
            "steps": [],
            "searches": [],
            "search_count": 0,
            "rewrite_count": 0,
            "model_attempts": 0,
        }
        try:
            state = self._graph.invoke(initial_state)
        except ProductSelectionAgentError:
            raise
        except Exception as error:
            raise self._map_model_error(error) from error

        products = (
            state.get("products", [])
            if state["assessment"].decision == SearchDecision.RETURN_RESULTS
            else []
        )
        matched_query = state["search_query"]
        candidates = [
            candidate
            for product in products
            for candidate in [self._candidate(product, matched_query)]
            if self._is_candidate_ready(product)
        ]
        outcome = (
            SelectionOutcome.CANDIDATES_FOUND if candidates else SelectionOutcome.NO_CANDIDATES
        )
        return ProductSelectionResult(
            selection_id=str(uuid4()),
            query=normalized_query,
            matched_query=matched_query,
            outcome=outcome,
            candidates=candidates,
            trace=ProductSelectionTrace(
                steps=state["steps"],
                searches=state["searches"],
                search_count=state["search_count"],
                rewrite_count=state["rewrite_count"],
                model_attempts=state["model_attempts"],
                stop_reason=("CANDIDATES_READY" if candidates else "NO_RELEVANT_CANDIDATES"),
                next_action=("MERCHANT_SELECTION" if candidates else "REFINE_QUERY"),
            ),
        )

    def _build_graph(self):
        builder = StateGraph(_SelectionState)
        builder.add_node("plan_query", self._plan_query)
        builder.add_node("search_products", self._search_products)
        builder.add_node("assess_results", self._assess_results)
        builder.add_node("rewrite_query", self._rewrite_query)
        builder.add_node("finalize", self._finalize)
        builder.add_edge(START, "plan_query")
        builder.add_edge("plan_query", "search_products")
        builder.add_edge("search_products", "assess_results")
        builder.add_conditional_edges(
            "assess_results",
            self._route_after_assessment,
            {"rewrite": "rewrite_query", "finalize": "finalize"},
        )
        builder.add_edge("rewrite_query", "search_products")
        builder.add_edge("finalize", END)
        return builder.compile()

    def _plan_query(self, state: _SelectionState) -> _SelectionState:
        search_query = self._reasoner.plan_query(state["user_query"]).strip()
        if not search_query:
            raise ProductSelectionAgentError("INVALID_SEARCH_QUERY", False)
        return {
            "search_query": search_query,
            "steps": [*state["steps"], "PLAN_QUERY"],
            "model_attempts": state["model_attempts"] + int(self._reasoner.uses_model),
        }

    def _search_products(self, state: _SelectionState) -> _SelectionState:
        started = time.perf_counter()
        try:
            page = self._search_tool.execute(state["search_query"], state["limit"])
        except CjApiError as error:
            raise ProductSelectionAgentError(
                "PRODUCT_SOURCE_UNAVAILABLE", error.retryable
            ) from error
        except Exception as error:
            raise ProductSelectionAgentError("PRODUCT_SOURCE_FAILED", False) from error
        search_trace = SelectionSearchTrace(
            query=state["search_query"],
            result_count=len(page.items),
            duration_ms=int((time.perf_counter() - started) * 1000),
        )
        return {
            "products": page.items,
            # 过滤后为空但上游仍有匹配项时，不通过改写或翻页补足数量。
            "allow_rewrite": bool(page.items) or page.total == 0,
            "search_count": state["search_count"] + 1,
            "searches": [*state["searches"], search_trace],
            "steps": [*state["steps"], "SEARCH_PRODUCTS"],
        }

    def _assess_results(self, state: _SelectionState) -> _SelectionState:
        can_rewrite = state["search_count"] < 2 and state.get("allow_rewrite", True)
        assessment = self._reasoner.assess(
            state["search_query"],
            state["products"],
            can_rewrite=can_rewrite,
        )
        return {
            "assessment": assessment,
            "steps": [*state["steps"], "ASSESS_RESULTS"],
            "model_attempts": state["model_attempts"] + int(self._reasoner.uses_model),
        }

    @staticmethod
    def _route_after_assessment(state: _SelectionState) -> str:
        assessment = state["assessment"]
        rewritten = (assessment.rewritten_query or "").strip()
        can_rewrite = (
            state["search_count"] < 2
            and state["rewrite_count"] < 1
            and state.get("allow_rewrite", True)
        )
        if (
            assessment.decision == SearchDecision.REWRITE_QUERY
            and can_rewrite
            and rewritten
            and rewritten.casefold() != state["search_query"].casefold()
        ):
            return "rewrite"
        return "finalize"

    @staticmethod
    def _rewrite_query(state: _SelectionState) -> _SelectionState:
        rewritten = (state["assessment"].rewritten_query or "").strip()
        return {
            "search_query": rewritten,
            "rewrite_count": state["rewrite_count"] + 1,
            "steps": [*state["steps"], "REWRITE_QUERY"],
        }

    @staticmethod
    def _finalize(state: _SelectionState) -> _SelectionState:
        return {"steps": [*state["steps"], "FINALIZE"]}

    @staticmethod
    def _candidate(product: SourceProduct, matched_query: str) -> CandidateProduct:
        if product.inventory_total is not None and product.inventory_total > 0:
            inventory_status = InventoryStatus.IN_STOCK
        elif product.inventory_total is None:
            inventory_status = InventoryStatus.UNKNOWN
        else:
            inventory_status = InventoryStatus.OUT_OF_STOCK
        return CandidateProduct(
            source_product_id=product.source_product_id,
            source=product.source.value,
            title=product.title,
            description=product.description,
            images=product.images,
            price=product.price,
            variants=product.variants,
            inventory_status=inventory_status,
            source_url=product.source_url,
            fetched_at=product.fetched_at,
            matched_query=matched_query,
            inventory_total=product.inventory_total,
        )

    @staticmethod
    def _is_candidate_ready(product: SourceProduct) -> bool:
        """只展示汇总库存为正且具备当前 Shopify 流程所需来源资料的商品。"""
        return bool(
            product.inventory_total is not None
            and product.inventory_total > 0
            and product.title.strip()
            and unescape(re.sub(r"<[^>]*>", "", product.description)).strip()
            and any(image.startswith("https://") for image in product.images)
            and product.price.amount > 0
            and 0 < len(product.variants) <= 250
            and all(
                variant.source_variant_id.strip()
                and variant.sku.strip()
                and variant.title.strip()
                and variant.price.amount > 0
                for variant in product.variants
            )
            and len({variant.source_variant_id for variant in product.variants})
            == len(product.variants)
            and len({variant.title.strip()[:255] for variant in product.variants})
            == len(product.variants)
        )

    @staticmethod
    def _map_model_error(error: Exception) -> ProductSelectionAgentError:
        if isinstance(error, openai.AuthenticationError):
            return ProductSelectionAgentError("INVALID_MODEL_CREDENTIALS", False)
        if isinstance(error, openai.PermissionDeniedError):
            return ProductSelectionAgentError("MODEL_PERMISSION_DENIED", False)
        if isinstance(error, openai.RateLimitError):
            return ProductSelectionAgentError("MODEL_RATE_LIMITED", True)
        if isinstance(error, openai.APITimeoutError):
            return ProductSelectionAgentError("MODEL_TIMEOUT", True)
        if isinstance(error, openai.APIConnectionError):
            return ProductSelectionAgentError("MODEL_NETWORK_ERROR", True)
        if isinstance(error, openai.InternalServerError):
            return ProductSelectionAgentError("MODEL_UPSTREAM_UNAVAILABLE", True)
        return ProductSelectionAgentError("MODEL_EXECUTION_FAILED", False)
