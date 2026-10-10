from typing import Protocol

from ecommerce_agent_ai.agents.product_selection.models import (
    ProductSelectionResult,
    SearchAssessment,
)
from ecommerce_agent_ai.product_sources.models import SourceProduct


class ProductSelectionAgent(Protocol):
    """选品 Agent 对业务调用方公开的最小接口。"""

    def select(self, query: str, limit: int = 10) -> ProductSelectionResult: ...


class ProductSelectionReasoner(Protocol):
    """选品图内部可替换的模型推理接口。"""

    uses_model: bool

    def plan_query(self, user_query: str) -> str: ...

    def assess(
        self,
        search_query: str,
        products: list[SourceProduct],
        *,
        can_rewrite: bool,
    ) -> SearchAssessment: ...
