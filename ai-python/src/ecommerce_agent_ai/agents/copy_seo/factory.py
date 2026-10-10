import os

from ecommerce_agent_ai.agents.copy_seo.agent import CopySeoReactAgent
from ecommerce_agent_ai.agents.copy_seo.fake import FakeCopySeoAgent
from ecommerce_agent_ai.agents.copy_seo.port import CopySeoAgent
from ecommerce_agent_ai.agents.llm import (
    DEFAULT_APINEBULA_BASE_URL as DEFAULT_APINEBULA_BASE_URL,
)
from ecommerce_agent_ai.agents.llm import (
    DEFAULT_APINEBULA_MODEL as DEFAULT_APINEBULA_MODEL,
)
from ecommerce_agent_ai.agents.llm import (
    build_agent_model as build_copy_seo_model,
)
from ecommerce_agent_ai.product_sources.factory import build_product_source
from ecommerce_agent_ai.product_sources.port import ProductSource


def build_copy_seo_agent(product_source: ProductSource | None = None) -> CopySeoAgent:
    source = product_source or build_product_source()
    if os.getenv("LLM_ENABLED", "false").lower() != "true":
        return FakeCopySeoAgent(source)
    max_revisions = _bounded_integer("COPY_SEO_MAX_REVISIONS", 1, 0, 3)
    max_tool_calls = _bounded_integer("COPY_SEO_MAX_TOOL_CALLS", 4, 1, 10)
    return CopySeoReactAgent(
        build_copy_seo_model(),
        source,
        max_revisions=max_revisions,
        max_tool_calls=max_tool_calls,
    )


def _bounded_integer(name: str, default: int, minimum: int, maximum: int) -> int:
    raw_value = os.getenv(name, str(default))
    try:
        value = int(raw_value)
    except ValueError as error:
        raise RuntimeError(f"{name} must be an integer") from error
    if value < minimum or value > maximum:
        raise RuntimeError(f"{name} must be between {minimum} and {maximum}")
    return value
