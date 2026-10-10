import time
from dataclasses import dataclass
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from pydantic import ValidationError

from ecommerce_agent_ai.agents.copy_seo.errors import CopySeoAgentError
from ecommerce_agent_ai.agents.copy_seo.factory import build_copy_seo_agent
from ecommerce_agent_ai.agents.copy_seo.models import CopySeoGeneration, CopySeoGenerationRequest
from ecommerce_agent_ai.agents.copy_seo.port import CopySeoAgent
from ecommerce_agent_ai.runtime.copy_seo_graph import DurableCopySeoGraph
from ecommerce_agent_ai.runtime.repository import ClaimedRun


@dataclass(frozen=True)
class ExecutionResult:
    artifact_type: str
    payload: dict[str, Any]
    agent_version: str
    prompt_version: str
    model: str


class RunExecutionError(RuntimeError):
    def __init__(self, error_type: str, retryable: bool) -> None:
        super().__init__(error_type)
        self.error_type = error_type
        self.retryable = retryable


class CopySeoRunExecutor:
    """把持久化任务输入映射到现有 Copy/SEO Agent。"""

    def __init__(
        self,
        agent: CopySeoAgent | None = None,
        *,
        checkpointer: BaseCheckpointSaver | None = None,
        transient_retries: int = 1,
        retry_delay_seconds: float = 1.0,
    ) -> None:
        self._agent = agent or build_copy_seo_agent()
        self._durable_graph = (
            DurableCopySeoGraph(self._agent, checkpointer) if checkpointer is not None else None
        )
        self._transient_retries = transient_retries
        self._retry_delay_seconds = retry_delay_seconds

    def execute(self, claimed: ClaimedRun) -> ExecutionResult:
        if claimed.agent_type != "COPY_SEO":
            raise RunExecutionError("UNSUPPORTED_AGENT_TYPE", False)
        try:
            request = CopySeoGenerationRequest.model_validate(claimed.request_payload)
        except ValidationError as error:
            raise RunExecutionError("INVALID_RUN_INPUT", False) from error

        for attempt in range(self._transient_retries + 1):
            try:
                generation = self._generate(claimed.run_id, request)
                return ExecutionResult(
                    artifact_type="COPY_SEO_RESULT",
                    payload=generation.model_dump(mode="json", by_alias=True),
                    agent_version=generation.execution.agent_version,
                    prompt_version=generation.execution.prompt_version,
                    model=generation.execution.model,
                )
            except CopySeoAgentError as error:
                if not error.retryable or attempt >= self._transient_retries:
                    raise RunExecutionError(error.error_type, error.retryable) from error
                if self._retry_delay_seconds > 0:
                    time.sleep(self._retry_delay_seconds)
        raise AssertionError("重试循环必须返回或抛出异常")

    def _generate(self, run_id: str, request: CopySeoGenerationRequest) -> CopySeoGeneration:
        if self._durable_graph is not None:
            return self._durable_graph.generate(
                run_id,
                request.source_product_id,
                request.target_market,
                request.language,
            )
        return self._agent.generate(
            request.source_product_id,
            request.target_market,
            request.language,
        )
