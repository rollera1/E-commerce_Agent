from uuid import uuid4

from ecommerce_agent_ai.agents.copy_seo.models import (
    AgentExecution,
    CopySeoContent,
    CopySeoGeneration,
    GenerationStatus,
    ReviewStatus,
)
from ecommerce_agent_ai.product_sources.models import SourceProduct


class FakeCopySeoAgent:
    """Deterministic agent used until a model credential is configured."""

    def generate(
        self, product: SourceProduct, target_market: str, language: str
    ) -> CopySeoGeneration:
        del target_market, language
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
                agent_version="fake-v1",
                prompt_version="copy-seo-v1",
                model="fake-deterministic",
                duration_ms=0,
            ),
        )
