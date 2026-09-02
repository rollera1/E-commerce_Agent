from typing import Protocol

from ecommerce_agent_ai.agents.copy_seo.models import CopySeoGeneration
from ecommerce_agent_ai.product_sources.models import SourceProduct


class CopySeoAgent(Protocol):
    def generate(
        self, product: SourceProduct, target_market: str, language: str
    ) -> CopySeoGeneration: ...
