from fastapi import APIRouter, HTTPException

from ecommerce_agent_ai.agents.copy_seo.errors import CopySeoAgentError
from ecommerce_agent_ai.agents.copy_seo.factory import build_copy_seo_agent
from ecommerce_agent_ai.agents.copy_seo.models import (
    CopySeoGeneration,
    CopySeoGenerationRequest,
)
from ecommerce_agent_ai.agents.copy_seo.port import CopySeoAgent
from ecommerce_agent_ai.product_sources.router import product_source

router = APIRouter(prefix="/internal/agents/copy-seo", tags=["agents"])
copy_seo_agent: CopySeoAgent = build_copy_seo_agent(product_source)


@router.post("/generations", response_model=CopySeoGeneration)
def generate_copy_seo(request: CopySeoGenerationRequest) -> CopySeoGeneration:
    try:
        return copy_seo_agent.generate(
            request.source_product_id,
            request.target_market,
            request.language,
        )
    except CopySeoAgentError as error:
        trace = (
            error.trace.model_dump(mode="json", by_alias=True) if error.trace is not None else None
        )
        status_code = 404 if error.error_type == "SOURCE_PRODUCT_NOT_FOUND" else 502
        raise HTTPException(
            status_code=status_code,
            detail={
                "errorCode": error.error_type,
                "message": "Copy/SEO Agent execution failed",
                "retryable": error.retryable,
                "nextAction": error.trace.next_action if error.trace else None,
                "trace": trace,
            },
        ) from error
