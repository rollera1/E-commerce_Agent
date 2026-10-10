from functools import lru_cache
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from ecommerce_agent_ai.runtime.api_models import (
    AgentRunResponse,
    SubmitAgentRunRequest,
    run_response,
)
from ecommerce_agent_ai.runtime.config import AgentRuntimeSettings
from ecommerce_agent_ai.runtime.database import create_runtime_engine
from ecommerce_agent_ai.runtime.repository import AgentRuntimeRepository

router = APIRouter(prefix="/internal/agent-runs", tags=["agent-runs"])


@lru_cache
def get_runtime_repository() -> AgentRuntimeRepository:
    settings = AgentRuntimeSettings.from_env()
    return AgentRuntimeRepository(create_runtime_engine(settings), settings.max_claims)


@router.post("", response_model=AgentRunResponse, status_code=status.HTTP_202_ACCEPTED)
def submit_agent_run(
    request: SubmitAgentRunRequest,
    repository: Annotated[AgentRuntimeRepository, Depends(get_runtime_repository)],
) -> AgentRunResponse:
    run = repository.submit(
        idempotency_key=request.idempotency_key,
        agent_type=request.agent_type,
        business_object_type=request.business_object_type,
        business_object_id=request.business_object_id,
        request_payload=request.input_payload,
    )
    return run_response(run)


@router.get("/{run_id}", response_model=AgentRunResponse)
def get_agent_run(
    run_id: str,
    repository: Annotated[AgentRuntimeRepository, Depends(get_runtime_repository)],
) -> AgentRunResponse:
    run = repository.get(run_id)
    if run is None:
        raise _not_found()
    artifact = repository.get_latest_artifact(run_id, "COPY_SEO_RESULT")
    return run_response(run, artifact.payload if artifact is not None else None)


@router.post("/{run_id}/cancel", response_model=AgentRunResponse)
def cancel_agent_run(
    run_id: str,
    repository: Annotated[AgentRuntimeRepository, Depends(get_runtime_repository)],
) -> AgentRunResponse:
    run = repository.request_cancel(run_id)
    if run is None:
        raise _not_found()
    return run_response(run)


def _not_found() -> HTTPException:
    return HTTPException(
        status_code=404,
        detail={"errorCode": "RUN_NOT_FOUND", "message": "Agent run was not found"},
    )
