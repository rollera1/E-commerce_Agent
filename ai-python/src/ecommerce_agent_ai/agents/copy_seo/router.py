from fastapi import APIRouter, HTTPException

from ecommerce_agent_ai.agents.copy_seo.fake import FakeCopySeoAgent
from ecommerce_agent_ai.agents.copy_seo.models import (
    CopySeoGeneration,
    CopySeoGenerationRequest,
)
from ecommerce_agent_ai.agents.copy_seo.port import CopySeoAgent
from ecommerce_agent_ai.product_sources.router import product_source

router = APIRouter(prefix="/internal/agents/copy-seo", tags=["agents"])
copy_seo_agent: CopySeoAgent = FakeCopySeoAgent()


@router.post("/generations", response_model=CopySeoGeneration)
def generate_copy_seo(request: CopySeoGenerationRequest) -> CopySeoGeneration:
    product = product_source.get_product(request.source_product_id)
    if product is None:
        raise HTTPException(
            status_code=404,
            detail={
                "errorCode": "SOURCE_PRODUCT_NOT_FOUND",
                "message": "Source product was not found",
            },
        )
    return copy_seo_agent.generate(product, request.target_market, request.language)
