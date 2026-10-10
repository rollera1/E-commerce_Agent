from typing import Protocol

from ecommerce_agent_ai.agents.copy_seo.models import CopySeoGeneration


class CopySeoAgent(Protocol):
    def generate(
        self, source_product_id: str, target_market: str, language: str
    ) -> CopySeoGeneration: ...
