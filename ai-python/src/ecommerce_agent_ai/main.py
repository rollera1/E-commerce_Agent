from fastapi import FastAPI
from pydantic import BaseModel

from ecommerce_agent_ai.agents.copy_seo.router import router as copy_seo_router
from ecommerce_agent_ai.agents.product_selection.router import (
    router as product_selection_router,
)
from ecommerce_agent_ai.product_sources.router import router as product_sources_router
from ecommerce_agent_ai.runtime.router import router as agent_runs_router


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str


app = FastAPI(
    title="Ecommerce Agent AI Service",
    version="0.1.0",
    description="Product-source adapter and future AI capabilities.",
)
app.include_router(product_sources_router)
app.include_router(copy_seo_router)
app.include_router(product_selection_router)
app.include_router(agent_runs_router)


@app.get("/health", response_model=HealthResponse, tags=["system"])
async def health() -> HealthResponse:
    return HealthResponse(status="ok", service="ai-python", version="0.1.0")
