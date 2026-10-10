from fastapi import APIRouter, HTTPException

from ecommerce_agent_ai.agents.product_selection.errors import ProductSelectionAgentError
from ecommerce_agent_ai.agents.product_selection.factory import build_product_selection_agent
from ecommerce_agent_ai.agents.product_selection.models import (
    ProductSelectionRequest,
    ProductSelectionResult,
)
from ecommerce_agent_ai.agents.product_selection.port import ProductSelectionAgent
from ecommerce_agent_ai.product_sources.router import product_source

router = APIRouter(prefix="/internal/agents/product-selection", tags=["agents"])
product_selection_agent: ProductSelectionAgent = build_product_selection_agent(product_source)


@router.post("/searches", response_model=ProductSelectionResult)
def search_candidate_products(request: ProductSelectionRequest) -> ProductSelectionResult:
    try:
        return product_selection_agent.select(request.query, request.limit)
    except ProductSelectionAgentError as error:
        raise HTTPException(
            status_code=502,
            detail={
                "errorCode": error.error_type,
                "message": "选品 Agent 执行失败",
                "retryable": error.retryable,
            },
        ) from error
