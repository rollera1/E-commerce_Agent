from datetime import datetime
from enum import StrEnum
from typing import Any, Literal

from pydantic import Field

from ecommerce_agent_ai.product_sources.models import ApiModel
from ecommerce_agent_ai.runtime.models import AgentRun, RunStatus


class AgentType(StrEnum):
    COPY_SEO = "COPY_SEO"


class SubmitAgentRunRequest(ApiModel):
    idempotency_key: str = Field(min_length=1, max_length=200)
    agent_type: Literal[AgentType.COPY_SEO]
    business_object_type: str = Field(min_length=1, max_length=50)
    business_object_id: str = Field(min_length=1, max_length=200)
    input_payload: dict[str, Any] = Field(alias="input")


class AgentRunError(ApiModel):
    error_type: str
    message: str
    retryable: bool


class AgentRunResponse(ApiModel):
    run_id: str
    agent_type: AgentType
    business_object_type: str
    business_object_id: str
    status: RunStatus
    stage: str
    claim_count: int
    cancel_requested: bool
    created_at: datetime
    started_at: datetime | None = None
    completed_at: datetime | None = None
    error: AgentRunError | None = None
    output: dict[str, Any] | None = None


def run_response(run: AgentRun, output: dict[str, Any] | None = None) -> AgentRunResponse:
    error = None
    if run.error_type is not None:
        error = AgentRunError(
            error_type=run.error_type,
            message=run.error_message or "Agent run failed",
            retryable=bool(run.retryable),
        )
    return AgentRunResponse(
        run_id=run.run_id,
        agent_type=AgentType(run.agent_type),
        business_object_type=run.business_object_type,
        business_object_id=run.business_object_id,
        status=RunStatus(run.status),
        stage=run.stage,
        claim_count=run.claim_count,
        cancel_requested=run.cancel_requested_at is not None,
        created_at=run.created_at,
        started_at=run.started_at,
        completed_at=run.completed_at,
        error=error,
        output=output,
    )
