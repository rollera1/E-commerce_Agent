import json

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage

from ecommerce_agent_ai.agents.product_selection.models import (
    SearchAssessment,
    SearchDecision,
    SearchQueryPlan,
)
from ecommerce_agent_ai.product_sources.models import SourceProduct


class LlmProductSelectionReasoner:
    """使用结构化模型输出规划关键词并判断检索结果。"""

    uses_model = True

    def __init__(self, model: BaseChatModel) -> None:
        self._query_planner = model.with_structured_output(SearchQueryPlan)
        self._result_assessor = model.with_structured_output(SearchAssessment)

    def plan_query(self, user_query: str) -> str:
        result = self._query_planner.invoke(
            [
                SystemMessage(
                    content=(
                        "你负责把商家的商品意图转换为适合商品库检索的简短关键词。"
                        "保留商品类型和必要属性，不添加用户未提出的品牌、认证或性能。"
                        "CJ 商品库以英文为主，因此中文意图应转换成自然英文关键词。"
                    )
                ),
                HumanMessage(content=user_query),
            ]
        )
        if not isinstance(result, SearchQueryPlan):
            raise TypeError("模型未返回 SearchQueryPlan")
        return result.keyword.strip()

    def assess(
        self,
        search_query: str,
        products: list[SourceProduct],
        *,
        can_rewrite: bool,
    ) -> SearchAssessment:
        summaries = [
            {
                "sourceProductId": product.source_product_id,
                "title": product.title,
                "description": product.description[:300],
            }
            for product in products
        ]
        result = self._result_assessor.invoke(
            [
                SystemMessage(
                    content=(
                        "判断商品检索结果是否与检索词相关。相关且非空时返回 RETURN_RESULTS；"
                        "无结果或明显低相关，并且允许改写时返回 REWRITE_QUERY 和一个更合适的"
                        "英文关键词；不允许改写或无法改善时返回 NO_RESULTS。"
                        "不得根据标题推断销量、热度或爆款属性。"
                    )
                ),
                HumanMessage(
                    content=json.dumps(
                        {
                            "searchQuery": search_query,
                            "canRewrite": can_rewrite,
                            "products": summaries,
                        },
                        ensure_ascii=False,
                    )
                ),
            ]
        )
        if not isinstance(result, SearchAssessment):
            raise TypeError("模型未返回 SearchAssessment")
        return result


class DeterministicSelectionReasoner:
    """关闭真实模型时使用的确定性选品推理替代实现。"""

    uses_model = False

    def plan_query(self, user_query: str) -> str:
        return user_query.strip()

    def assess(
        self,
        search_query: str,
        products: list[SourceProduct],
        *,
        can_rewrite: bool,
    ) -> SearchAssessment:
        del search_query, can_rewrite
        if products:
            return SearchAssessment(
                decision=SearchDecision.RETURN_RESULTS,
                reason="检索返回了候选商品",
            )
        return SearchAssessment(
            decision=SearchDecision.NO_RESULTS,
            reason="确定性替代实现不会自行改写关键词",
        )
