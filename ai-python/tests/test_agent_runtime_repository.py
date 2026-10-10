from datetime import UTC, datetime, timedelta

from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from ecommerce_agent_ai.runtime.models import AgentEvent, AgentRunAttempt, Base, RunStatus
from ecommerce_agent_ai.runtime.repository import AgentRuntimeRepository


def runtime_repository(max_claims: int = 3) -> AgentRuntimeRepository:
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    return AgentRuntimeRepository(engine, max_claims=max_claims)


def submit(repository: AgentRuntimeRepository, key: str = "copy:p1:v1"):
    return repository.submit(
        idempotency_key=key,
        agent_type="COPY_SEO",
        business_object_type="PRODUCT_SELECTION",
        business_object_id="selection-1",
        request_payload={"sourceProductId": "p1", "language": "en"},
    )


def test_submit_is_idempotent_and_preserves_first_request() -> None:
    repository = runtime_repository()

    first = submit(repository)
    second = repository.submit(
        idempotency_key="copy:p1:v1",
        agent_type="COPY_SEO",
        business_object_type="PRODUCT_SELECTION",
        business_object_id="selection-1",
        request_payload={"sourceProductId": "changed"},
    )

    assert second.run_id == first.run_id
    assert second.request_payload["sourceProductId"] == "p1"
    assert second.status == RunStatus.SUBMITTED


def test_expired_lease_can_be_reclaimed_and_stale_worker_is_fenced() -> None:
    repository = runtime_repository()
    run = submit(repository)
    start = datetime(2026, 10, 11, tzinfo=UTC)

    first = repository.claim_next(worker_id="worker-1", lease_seconds=120, now=start)
    assert first is not None
    assert first.run_id == run.run_id
    assert first.lease_version == 1
    assert repository.renew_lease(
        run_id=run.run_id,
        worker_id="worker-1",
        lease_version=1,
        lease_seconds=120,
        now=start + timedelta(seconds=30),
    )

    second = repository.claim_next(
        worker_id="worker-2", lease_seconds=120, now=start + timedelta(seconds=151)
    )
    assert second is not None
    assert second.lease_version == 2
    assert second.claim_count == 2
    assert not repository.complete(
        run_id=run.run_id,
        worker_id="worker-1",
        lease_version=1,
        artifact_type="COPY_SEO_RESULT",
        payload={"title": "stale"},
        agent_version="v1",
        prompt_version="p1",
        model="fake",
    )
    assert repository.complete(
        run_id=run.run_id,
        worker_id="worker-2",
        lease_version=2,
        artifact_type="COPY_SEO_RESULT",
        payload={"title": "fresh"},
        agent_version="v1",
        prompt_version="p1",
        model="fake",
    )
    assert repository.get(run.run_id).status == RunStatus.SUCCEEDED  # type: ignore[union-attr]
    assert repository.get_latest_artifact(run.run_id, "COPY_SEO_RESULT").payload == {  # type: ignore[union-attr]
        "title": "fresh"
    }


def test_running_cancel_is_cooperative_and_blocks_late_success() -> None:
    repository = runtime_repository()
    run = submit(repository)
    claim = repository.claim_next(worker_id="worker-1", lease_seconds=120)
    assert claim is not None

    cancelled = repository.request_cancel(run.run_id)
    assert cancelled is not None
    assert cancelled.status == RunStatus.RUNNING
    assert repository.is_cancel_requested(run.run_id)
    assert not repository.complete(
        run_id=run.run_id,
        worker_id="worker-1",
        lease_version=claim.lease_version,
        artifact_type="COPY_SEO_RESULT",
        payload={"title": "too late"},
        agent_version="v1",
        prompt_version="p1",
        model="fake",
    )
    assert repository.mark_cancelled(
        run_id=run.run_id,
        worker_id="worker-1",
        lease_version=claim.lease_version,
    )
    assert repository.get(run.run_id).status == RunStatus.CANCELLED  # type: ignore[union-attr]


def test_submitted_cancel_is_immediately_terminal() -> None:
    repository = runtime_repository()
    run = submit(repository)

    cancelled = repository.request_cancel(run.run_id)

    assert cancelled is not None
    assert cancelled.status == RunStatus.CANCELLED
    assert repository.claim_next(worker_id="worker-1", lease_seconds=120) is None


def test_max_claims_turns_expired_run_into_explicit_failure() -> None:
    repository = runtime_repository(max_claims=1)
    run = submit(repository)
    start = datetime(2026, 10, 11, tzinfo=UTC)
    assert repository.claim_next(worker_id="worker-1", lease_seconds=10, now=start)

    assert repository.fail_exhausted_leases(now=start + timedelta(seconds=11)) == 1
    failed = repository.get(run.run_id)
    assert failed is not None
    assert failed.status == RunStatus.FAILED
    assert failed.error_type == "MAX_CLAIMS_EXCEEDED"


def test_attempts_and_events_record_only_summaries() -> None:
    repository = runtime_repository()
    run = submit(repository)
    claim = repository.claim_next(worker_id="worker-1", lease_seconds=120)
    assert claim is not None
    assert repository.update_stage(
        run_id=run.run_id,
        worker_id="worker-1",
        lease_version=claim.lease_version,
        stage="GENERATING",
        summary={"sourceProductId": "p1"},
    )
    assert repository.fail(
        run_id=run.run_id,
        worker_id="worker-1",
        lease_version=claim.lease_version,
        error_type="MODEL_TIMEOUT",
        error_message="timed out",
        retryable=True,
    )

    with Session(repository._engine) as session:  # noqa: SLF001
        attempts = session.scalars(
            select(AgentRunAttempt).where(AgentRunAttempt.run_id == run.run_id)
        ).all()
        events = session.scalars(
            select(AgentEvent).where(AgentEvent.run_id == run.run_id).order_by(AgentEvent.sequence)
        ).all()
    assert len(attempts) == 1
    assert attempts[0].error_type == "MODEL_TIMEOUT"
    assert [event.event_type for event in events] == [
        "RUN_SUBMITTED",
        "RUN_CLAIMED",
        "STAGE_CHANGED",
        "RUN_FAILED",
    ]
    assert all("reasoning" not in event.summary for event in events)
