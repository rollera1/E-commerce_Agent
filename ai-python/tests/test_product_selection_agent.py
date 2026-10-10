from collections.abc import Iterable

import pytest

from ecommerce_agent_ai.agents.product_selection.agent import ProductSelectionGraphAgent
from ecommerce_agent_ai.agents.product_selection.errors import ProductSelectionAgentError
from ecommerce_agent_ai.agents.product_selection.models import (
    SearchAssessment,
    SearchDecision,
)
from ecommerce_agent_ai.product_sources.cj_error import CjApiError
from ecommerce_agent_ai.product_sources.fake import FakeProductSource
from ecommerce_agent_ai.product_sources.models import SourceProduct, SourceProductPage


class ScriptedReasoner:
    uses_model = True

    def __init__(
        self,
        planned_query: str,
        assessments: Iterable[SearchAssessment],
    ) -> None:
        self.planned_query = planned_query
        self.assessments = iter(assessments)
        self.plan_calls: list[str] = []
        self.assessment_calls: list[tuple[str, bool]] = []

    def plan_query(self, user_query: str) -> str:
        self.plan_calls.append(user_query)
        return self.planned_query

    def assess(
        self,
        search_query: str,
        products: list[SourceProduct],
        *,
        can_rewrite: bool,
    ) -> SearchAssessment:
        del products
        self.assessment_calls.append((search_query, can_rewrite))
        return next(self.assessments)


class RecordingProductSource(FakeProductSource):
    def __init__(self, results_by_keyword: dict[str, list[SourceProduct]]) -> None:
        super().__init__()
        self.results_by_keyword = results_by_keyword
        self.calls: list[tuple[str | None, int, int]] = []

    def list_products(
        self,
        keyword: str | None,
        page: int,
        page_size: int,
    ) -> SourceProductPage:
        self.calls.append((keyword, page, page_size))
        products = self.results_by_keyword.get(keyword or "", [])
        return SourceProductPage(
            items=products[:page_size],
            page=page,
            page_size=page_size,
            total=len(products),
        )


def fake_product() -> SourceProduct:
    product = FakeProductSource().get_product("fake-desk-lamp-001")
    assert product is not None
    return product


def assessment(
    decision: SearchDecision,
    *,
    rewritten_query: str | None = None,
) -> SearchAssessment:
    return SearchAssessment(
        decision=decision,
        reason="测试判断",
        rewritten_query=rewritten_query,
    )


def test_returns_candidates_after_one_tool_search() -> None:
    source = RecordingProductSource({"desk lamp": [fake_product()]})
    reasoner = ScriptedReasoner(
        "desk lamp",
        [assessment(SearchDecision.RETURN_RESULTS)],
    )

    result = ProductSelectionGraphAgent(reasoner, source).select("书桌灯", 5)

    assert source.calls == [("desk lamp", 1, 5)]
    assert result.matched_query == "desk lamp"
    assert result.candidates[0].source_product_id == "fake-desk-lamp-001"
    assert result.candidates[0].matched_query == "desk lamp"
    assert result.candidates[0].inventory_status == "IN_STOCK"
    assert result.trace.rewrite_count == 0
    assert result.trace.model_attempts == 2
    assert result.trace.next_action == "MERCHANT_SELECTION"
    assert result.trace.steps == [
        "PLAN_QUERY",
        "SEARCH_PRODUCTS",
        "ASSESS_RESULTS",
        "FINALIZE",
    ]


def test_rewrites_keyword_once_when_first_result_is_not_usable() -> None:
    source = RecordingProductSource({"pet leash": [], "dog walking leash": [fake_product()]})
    reasoner = ScriptedReasoner(
        "pet leash",
        [
            assessment(
                SearchDecision.REWRITE_QUERY,
                rewritten_query="dog walking leash",
            ),
            assessment(SearchDecision.RETURN_RESULTS),
        ],
    )

    result = ProductSelectionGraphAgent(reasoner, source).select("宠物牵引绳", 10)

    assert source.calls == [
        ("pet leash", 1, 10),
        ("dog walking leash", 1, 10),
    ]
    assert reasoner.assessment_calls == [
        ("pet leash", True),
        ("dog walking leash", False),
    ]
    assert result.matched_query == "dog walking leash"
    assert result.trace.rewrite_count == 1
    assert result.trace.search_count == 2
    assert result.trace.steps.count("REWRITE_QUERY") == 1


def test_stops_after_second_empty_search_even_if_reasoner_requests_another_rewrite() -> None:
    source = RecordingProductSource({})
    reasoner = ScriptedReasoner(
        "pet leash",
        [
            assessment(SearchDecision.REWRITE_QUERY, rewritten_query="dog leash"),
            assessment(SearchDecision.REWRITE_QUERY, rewritten_query="walking leash"),
        ],
    )

    result = ProductSelectionGraphAgent(reasoner, source).select("宠物牵引绳", 10)

    assert source.calls == [("pet leash", 1, 10), ("dog leash", 1, 10)]
    assert result.candidates == []
    assert result.outcome == "NO_CANDIDATES"
    assert result.trace.search_count == 2
    assert result.trace.rewrite_count == 1
    assert result.trace.next_action == "REFINE_QUERY"


def test_does_not_treat_upstream_failure_as_an_empty_search() -> None:
    class FailingProductSource(FakeProductSource):
        def list_products(self, keyword, page, page_size):
            del keyword, page, page_size
            raise CjApiError(1600200, "request-id", True)

    reasoner = ScriptedReasoner("pet leash", [])

    with pytest.raises(ProductSelectionAgentError) as raised:
        ProductSelectionGraphAgent(reasoner, FailingProductSource()).select("宠物牵引绳", 10)

    assert raised.value.error_type == "PRODUCT_SOURCE_UNAVAILABLE"
    assert raised.value.retryable is True
    assert "request-id" not in str(raised.value)


@pytest.mark.parametrize("query,limit", [("", 10), ("lamp", 0), ("lamp", 101)])
def test_rejects_invalid_direct_calls(query: str, limit: int) -> None:
    reasoner = ScriptedReasoner("lamp", [])

    with pytest.raises(ValueError):
        ProductSelectionGraphAgent(reasoner, FakeProductSource()).select(query, limit)


@pytest.mark.parametrize("inventory,expected_count", [(None, 0), (0, 0), (5, 1)])
def test_selection_filters_by_product_summary_inventory(inventory, expected_count) -> None:
    product = fake_product()
    product = product.model_copy(update={"inventory_total": inventory})
    source = RecordingProductSource({"lamp": [product]})
    reasoner = ScriptedReasoner("lamp", [assessment(SearchDecision.RETURN_RESULTS)])
    result = ProductSelectionGraphAgent(reasoner, source).select("lamp")
    assert len(result.candidates) == expected_count
    assert len(source.calls) == 1
    if expected_count:
        assert result.candidates[0].inventory_total == inventory
        assert result.candidates[0].inventory_status == "IN_STOCK"


@pytest.mark.parametrize("variant_inventory", [None, 0, 7])
def test_variant_inventory_does_not_replace_summary_or_filter_candidates(variant_inventory) -> None:
    product = fake_product()
    product = product.model_copy(
        update={
            "variants": [
                variant.model_copy(update={"inventory_available": variant_inventory})
                for variant in product.variants
            ]
        }
    )
    source = RecordingProductSource({"lamp": [product]})
    reasoner = ScriptedReasoner("lamp", [assessment(SearchDecision.RETURN_RESULTS)])
    result = ProductSelectionGraphAgent(reasoner, source).select("lamp", 100)
    assert len(result.candidates) == 1
    assert result.candidates[0].inventory_total == 50
    assert source.calls == [("lamp", 1, 100)]


@pytest.mark.parametrize(
    "missing", ["title", "description", "images", "variants", "sku", "variant_id", "price"]
)
def test_filters_missing_shopify_source_information(missing) -> None:
    product = fake_product()
    if missing in {"title", "description"}:
        product = product.model_copy(update={missing: ""})
    elif missing in {"images", "variants"}:
        product = product.model_copy(update={missing: []})
    elif missing == "price":
        product = product.model_copy(
            update={"price": product.price.model_copy(update={"amount": 0})}
        )
    else:
        field = "sku" if missing == "sku" else "source_variant_id"
        product = product.model_copy(
            update={"variants": [product.variants[0].model_copy(update={field: ""})]}
        )
    source = RecordingProductSource({"lamp": [product]})
    reasoner = ScriptedReasoner("lamp", [assessment(SearchDecision.RETURN_RESULTS)])
    assert ProductSelectionGraphAgent(reasoner, source).select("lamp").candidates == []
    assert len(source.calls) == 1


def test_filtering_does_not_trigger_a_rewrite_to_refill_empty_candidates() -> None:
    class FilteredSource(RecordingProductSource):
        def list_products(self, keyword, page, page_size):
            self.calls.append((keyword, page, page_size))
            return SourceProductPage(items=[], page=page, page_size=page_size, total=12)

    source = FilteredSource({})
    reasoner = ScriptedReasoner(
        "lamp", [assessment(SearchDecision.REWRITE_QUERY, rewritten_query="desk lamp")]
    )
    result = ProductSelectionGraphAgent(reasoner, source).select("lamp")
    assert result.candidates == []
    assert source.calls == [("lamp", 1, 10)]
    assert reasoner.assessment_calls == [("lamp", False)]


def test_duplicate_shopify_option_values_are_not_listed() -> None:
    product = fake_product()
    product = product.model_copy(
        update={
            "variants": [
                product.variants[0],
                product.variants[0].model_copy(
                    update={"source_variant_id": "other-id", "sku": "other-sku"}
                ),
            ]
        }
    )
    source = RecordingProductSource({"lamp": [product]})
    reasoner = ScriptedReasoner("lamp", [assessment(SearchDecision.RETURN_RESULTS)])
    assert ProductSelectionGraphAgent(reasoner, source).select("lamp").candidates == []
