import os

from ecommerce_agent_ai.agents.llm import build_agent_model
from ecommerce_agent_ai.agents.product_selection.agent import ProductSelectionGraphAgent
from ecommerce_agent_ai.agents.product_selection.port import ProductSelectionAgent
from ecommerce_agent_ai.agents.product_selection.reasoner import (
    DeterministicSelectionReasoner,
    LlmProductSelectionReasoner,
)
from ecommerce_agent_ai.product_sources.factory import build_product_source
from ecommerce_agent_ai.product_sources.port import ProductSource


def build_product_selection_agent(
    product_source: ProductSource | None = None,
) -> ProductSelectionAgent:
    source = product_source or build_product_source()
    if os.getenv("LLM_ENABLED", "false").lower() == "true":
        reasoner = LlmProductSelectionReasoner(build_agent_model())
    else:
        reasoner = DeterministicSelectionReasoner()
    return ProductSelectionGraphAgent(reasoner, source)
