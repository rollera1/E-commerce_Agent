from uuid import uuid4

from ecommerce_agent_ai.agents.copy_seo.errors import CopySeoAgentError
from ecommerce_agent_ai.agents.copy_seo.models import (
    AgentExecution,
    AgentOutcome,
    AgentTrace,
    CopySeoContent,
    CopySeoGeneration,
    GenerationStatus,
    ReviewStatus,
    ToolCallStatus,
    ToolCallTrace,
)
from ecommerce_agent_ai.product_sources.fake import FakeProductSource
from ecommerce_agent_ai.product_sources.port import ProductSource


class FakeCopySeoAgent:
    """关闭真实模型访问时使用的确定性 Agent 替代实现。"""

    def __init__(self, product_source: ProductSource | None = None) -> None:
        self._product_source = product_source or FakeProductSource()

    def generate(
        self,
        source_product_id: str,
        target_market: str,
        language: str,
    ) -> CopySeoGeneration:
        del target_market, language
        product = self._product_source.get_product(source_product_id)
        if product is None:
            trace = AgentTrace(
                current_step="TOOL_LOOP",
                outcome=AgentOutcome.NEEDS_HUMAN_INPUT,
                steps=["MODEL_DECIDE", "TOOL_LOOP"],
                tool_calls=[
                    ToolCallTrace(
                        tool="get_product_facts",
                        status=ToolCallStatus.FAILED,
                        duration_ms=0,
                        error_type="SOURCE_PRODUCT_NOT_FOUND",
                    )
                ],
                model_attempts=0,
                next_action="HUMAN_INPUT_REQUIRED",
            )
            raise CopySeoAgentError("SOURCE_PRODUCT_NOT_FOUND", False, trace=trace)
        return CopySeoGeneration(
            generation_id=str(uuid4()),
            source_product_id=product.source_product_id,
            status=GenerationStatus.SUCCEEDED,
            review_status=ReviewStatus.PENDING_REVIEW,
            content=CopySeoContent(
                title="Adjustable LED Desk Lamp for Focused Workspaces",
                bullet_points=[
                    "Adjustable lighting for focused desk work",
                    "Compact profile suited to home-office spaces",
                    "Dimmable LED light for everyday use",
                ],
                description=(
                    "Create a comfortable home-office setup with this compact adjustable "
                    "LED desk lamp. Its dimmable light helps tailor your workspace for focused "
                    "tasks while the space-conscious design keeps your desk organized."
                ),
                seo_title="Adjustable LED Desk Lamp | Home Office Lighting",
                meta_description=(
                    "Shop a compact adjustable LED desk lamp with dimmable lighting for "
                    "focused work and organized home-office spaces."
                ),
                tags=["desk lamp", "LED lighting", "home office"],
            ),
            execution=AgentExecution(
                agent="COPY_SEO",
                agent_version="copy-seo-fake-v2",
                prompt_version="copy-seo-react-v1",
                model="fake-deterministic",
                duration_ms=0,
            ),
            trace=AgentTrace(
                current_step="AWAIT_MERCHANT_REVIEW",
                outcome=AgentOutcome.COMPLETED,
                steps=["MODEL_DECIDE", "TOOL_LOOP", "STRUCTURED_OUTPUT", "VALIDATE"],
                tool_calls=[
                    ToolCallTrace(
                        tool="get_product_facts",
                        status=ToolCallStatus.SUCCEEDED,
                        duration_ms=0,
                        summary={
                            "sourceProductId": product.source_product_id,
                            "variantCount": len(product.variants),
                            "source": product.source.value,
                        },
                    ),
                    ToolCallTrace(
                        tool="validate_copy_seo",
                        status=ToolCallStatus.SUCCEEDED,
                        duration_ms=0,
                        summary={
                            "passed": True,
                            "issueCount": 0,
                            "ruleVersion": "copy-compliance-v2",
                        },
                    ),
                ],
                model_attempts=1,
                revision_count=0,
                next_action="MERCHANT_REVIEW",
            ),
        )
