from langgraph.checkpoint.memory import InMemorySaver
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool

from ecommerce_agent_ai.agents.copy_seo.errors import CopySeoAgentError
from ecommerce_agent_ai.agents.copy_seo.fake import FakeCopySeoAgent
from ecommerce_agent_ai.runtime.config import AgentRuntimeSettings
from ecommerce_agent_ai.runtime.executor import (
    CopySeoRunExecutor,
    ExecutionResult,
    RunExecutionError,
)
from ecommerce_agent_ai.runtime.models import Base, RunStatus
from ecommerce_agent_ai.runtime.repository import AgentRuntimeRepository, ClaimedRun
from ecommerce_agent_ai.runtime.worker import AgentWorker


def runtime() -> tuple[AgentRuntimeRepository, AgentRuntimeSettings]:
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    return AgentRuntimeRepository(engine), AgentRuntimeSettings(
        database_url="sqlite://",
        lease_seconds=120,
        heartbeat_seconds=30,
        max_claims=3,
        poll_interval_seconds=0.01,
    )


def submit(repository: AgentRuntimeRepository):
    return repository.submit(
        idempotency_key="selection-1:copy-seo:v1",
        agent_type="COPY_SEO",
        business_object_type="PRODUCT_SELECTION",
        business_object_id="selection-1",
        request_payload={
            "sourceProductId": "fake-desk-lamp-001",
            "targetMarket": "US",
            "language": "en",
        },
    )


def successful_result() -> ExecutionResult:
    return ExecutionResult(
        artifact_type="COPY_SEO_RESULT",
        payload={"title": "Generated title"},
        agent_version="agent-v1",
        prompt_version="prompt-v1",
        model="fake",
    )


def test_worker_executes_and_persists_one_result() -> None:
    repository, settings = runtime()
    run = submit(repository)
    worker = AgentWorker(repository, lambda claimed: successful_result(), settings, "worker-1")

    assert worker.run_once()
    assert not worker.run_once()
    completed = repository.get(run.run_id)
    assert completed is not None
    assert completed.status == RunStatus.SUCCEEDED
    assert completed.claim_count == 1
    assert repository.get_latest_artifact(run.run_id, "COPY_SEO_RESULT").payload == {  # type: ignore[union-attr]
        "title": "Generated title"
    }


def test_worker_honours_cancel_requested_during_execution() -> None:
    repository, settings = runtime()
    run = submit(repository)

    def cancel_then_return(claimed: ClaimedRun) -> ExecutionResult:
        repository.request_cancel(claimed.run_id)
        return successful_result()

    worker = AgentWorker(repository, cancel_then_return, settings, "worker-1")
    assert worker.run_once()
    cancelled = repository.get(run.run_id)
    assert cancelled is not None
    assert cancelled.status == RunStatus.CANCELLED
    assert repository.get_latest_artifact(run.run_id, "COPY_SEO_RESULT") is None


def test_worker_persists_sanitized_failure() -> None:
    repository, settings = runtime()
    run = submit(repository)

    def fail(claimed: ClaimedRun) -> ExecutionResult:
        del claimed
        raise RunExecutionError("RATE_LIMITED", True)

    worker = AgentWorker(repository, fail, settings, "worker-1")
    assert worker.run_once()
    failed = repository.get(run.run_id)
    assert failed is not None
    assert failed.status == RunStatus.FAILED
    assert failed.error_type == "RATE_LIMITED"
    assert failed.error_message == "Agent execution failed"
    assert failed.retryable is True


def test_copy_executor_retries_one_transient_failure() -> None:
    class FailOnceAgent:
        def __init__(self) -> None:
            self.calls = 0
            self.delegate = FakeCopySeoAgent()

        def generate(self, source_product_id: str, target_market: str, language: str):
            self.calls += 1
            if self.calls == 1:
                raise CopySeoAgentError("TIMEOUT", True)
            return self.delegate.generate(source_product_id, target_market, language)

    agent = FailOnceAgent()
    executor = CopySeoRunExecutor(agent, retry_delay_seconds=0)
    result = executor.execute(
        ClaimedRun(
            run_id="run-1",
            agent_type="COPY_SEO",
            request_payload={
                "sourceProductId": "fake-desk-lamp-001",
                "targetMarket": "US",
                "language": "en",
            },
            worker_id="worker-1",
            lease_version=1,
            claim_count=1,
        )
    )
    assert agent.calls == 2
    assert result.payload["reviewStatus"] == "PENDING_REVIEW"


def test_copy_executor_reuses_durable_structured_result() -> None:
    class CountingAgent:
        def __init__(self) -> None:
            self.calls = 0
            self.delegate = FakeCopySeoAgent()

        def generate(self, source_product_id: str, target_market: str, language: str):
            self.calls += 1
            return self.delegate.generate(source_product_id, target_market, language)

    agent = CountingAgent()
    checkpointer = InMemorySaver()
    first_executor = CopySeoRunExecutor(
        agent,
        checkpointer=checkpointer,
        retry_delay_seconds=0,
    )
    claimed = ClaimedRun(
        run_id="durable-run-1",
        agent_type="COPY_SEO",
        request_payload={
            "sourceProductId": "fake-desk-lamp-001",
            "targetMarket": "US",
            "language": "en",
        },
        worker_id="worker-1",
        lease_version=1,
        claim_count=1,
    )

    first = first_executor.execute(claimed)
    restarted_executor = CopySeoRunExecutor(
        agent,
        checkpointer=checkpointer,
        retry_delay_seconds=0,
    )
    second = restarted_executor.execute(claimed)

    assert agent.calls == 1
    assert second.payload == first.payload
