from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid4

from sqlalchemy import Engine, func, or_, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ecommerce_agent_ai.runtime.models import (
    AgentArtifact,
    AgentEvent,
    AgentRun,
    AgentRunAttempt,
    AttemptStatus,
    RunStatus,
)


@dataclass(frozen=True)
class ClaimedRun:
    run_id: str
    agent_type: str
    request_payload: dict[str, Any]
    worker_id: str
    lease_version: int
    claim_count: int


class AgentRuntimeRepository:
    """用数据库约束实现幂等提交、租约领取和 fencing。"""

    def __init__(self, engine: Engine, max_claims: int = 3) -> None:
        self._engine = engine
        self._max_claims = max_claims

    def submit(
        self,
        *,
        idempotency_key: str,
        agent_type: str,
        business_object_type: str,
        business_object_id: str,
        request_payload: dict[str, Any],
        now: datetime | None = None,
    ) -> AgentRun:
        current_time = now or datetime.now(UTC)
        run = AgentRun(
            run_id=str(uuid4()),
            idempotency_key=idempotency_key,
            agent_type=agent_type,
            business_object_type=business_object_type,
            business_object_id=business_object_id,
            status=RunStatus.SUBMITTED,
            stage="QUEUED",
            request_payload=request_payload,
            max_claims=self._max_claims,
            available_at=current_time,
            created_at=current_time,
            updated_at=current_time,
        )
        with Session(self._engine) as session:
            session.add(run)
            self._append_event(session, run, "RUN_SUBMITTED", "QUEUED", {})
            try:
                session.commit()
            except IntegrityError:
                session.rollback()
                existing = session.scalar(
                    select(AgentRun).where(AgentRun.idempotency_key == idempotency_key)
                )
                if existing is None:
                    raise
                session.expunge(existing)
                return existing
            session.refresh(run)
            session.expunge(run)
            return run

    def get(self, run_id: str) -> AgentRun | None:
        with Session(self._engine) as session:
            run = session.get(AgentRun, run_id)
            if run is not None:
                session.expunge(run)
            return run

    def get_latest_artifact(self, run_id: str, artifact_type: str) -> AgentArtifact | None:
        with Session(self._engine) as session:
            artifact = session.scalar(
                select(AgentArtifact)
                .where(
                    AgentArtifact.run_id == run_id,
                    AgentArtifact.artifact_type == artifact_type,
                )
                .order_by(AgentArtifact.version.desc())
                .limit(1)
            )
            if artifact is not None:
                session.expunge(artifact)
            return artifact

    def claim_next(
        self,
        *,
        worker_id: str,
        lease_seconds: int,
        now: datetime | None = None,
    ) -> ClaimedRun | None:
        current_time = now or datetime.now(UTC)
        with Session(self._engine) as session, session.begin():
            run = session.scalar(
                select(AgentRun)
                .where(
                    or_(
                        AgentRun.status == RunStatus.SUBMITTED,
                        (
                            (AgentRun.status == RunStatus.RUNNING)
                            & (AgentRun.lease_expires_at < current_time)
                        ),
                    ),
                    AgentRun.available_at <= current_time,
                    AgentRun.cancel_requested_at.is_(None),
                    AgentRun.claim_count < AgentRun.max_claims,
                )
                .order_by(AgentRun.created_at, AgentRun.run_id)
                .with_for_update(skip_locked=True)
                .limit(1)
            )
            if run is None:
                return None

            if run.status == RunStatus.RUNNING:
                self._finish_current_attempt(
                    session, run, AttemptStatus.LEASE_EXPIRED, current_time
                )
            run.status = RunStatus.RUNNING
            run.stage = "CLAIMED"
            run.claim_count += 1
            run.lease_version += 1
            run.lease_owner = worker_id
            run.lease_expires_at = current_time + timedelta(seconds=lease_seconds)
            run.started_at = run.started_at or current_time
            run.updated_at = current_time
            session.add(
                AgentRunAttempt(
                    run_id=run.run_id,
                    attempt_number=run.claim_count,
                    worker_id=worker_id,
                    lease_version=run.lease_version,
                    status=AttemptStatus.RUNNING,
                    started_at=current_time,
                )
            )
            self._append_event(
                session,
                run,
                "RUN_CLAIMED",
                "CLAIMED",
                {"claimCount": run.claim_count, "leaseVersion": run.lease_version},
            )
            return ClaimedRun(
                run_id=run.run_id,
                agent_type=run.agent_type,
                request_payload=dict(run.request_payload),
                worker_id=worker_id,
                lease_version=run.lease_version,
                claim_count=run.claim_count,
            )

    def renew_lease(
        self,
        *,
        run_id: str,
        worker_id: str,
        lease_version: int,
        lease_seconds: int,
        now: datetime | None = None,
    ) -> bool:
        current_time = now or datetime.now(UTC)
        with Session(self._engine) as session, session.begin():
            result = session.execute(
                update(AgentRun)
                .where(*self._fence(run_id, worker_id, lease_version, current_time))
                .values(
                    lease_expires_at=current_time + timedelta(seconds=lease_seconds),
                    updated_at=current_time,
                )
            )
            return result.rowcount == 1

    def update_stage(
        self,
        *,
        run_id: str,
        worker_id: str,
        lease_version: int,
        stage: str,
        summary: dict[str, Any] | None = None,
        now: datetime | None = None,
    ) -> bool:
        current_time = now or datetime.now(UTC)
        with Session(self._engine) as session, session.begin():
            run = self._locked_fenced_run(session, run_id, worker_id, lease_version, current_time)
            if run is None:
                return False
            run.stage = stage
            run.updated_at = current_time
            self._append_event(session, run, "STAGE_CHANGED", stage, summary or {})
            return True

    def complete(
        self,
        *,
        run_id: str,
        worker_id: str,
        lease_version: int,
        artifact_type: str,
        payload: dict[str, Any],
        agent_version: str,
        prompt_version: str,
        model: str,
        now: datetime | None = None,
    ) -> bool:
        current_time = now or datetime.now(UTC)
        with Session(self._engine) as session, session.begin():
            run = self._locked_fenced_run(session, run_id, worker_id, lease_version, current_time)
            if run is None or run.cancel_requested_at is not None:
                return False
            latest_version = session.scalar(
                select(func.max(AgentArtifact.version)).where(
                    AgentArtifact.run_id == run_id,
                    AgentArtifact.artifact_type == artifact_type,
                )
            )
            session.add(
                AgentArtifact(
                    run_id=run_id,
                    artifact_type=artifact_type,
                    version=(latest_version or 0) + 1,
                    payload=payload,
                    created_at=current_time,
                )
            )
            run.status = RunStatus.SUCCEEDED
            run.stage = "COMPLETED"
            run.agent_version = agent_version
            run.prompt_version = prompt_version
            run.model = model
            run.completed_at = current_time
            run.updated_at = current_time
            self._clear_lease(run)
            self._finish_current_attempt(session, run, AttemptStatus.SUCCEEDED, current_time)
            self._append_event(session, run, "RUN_SUCCEEDED", "COMPLETED", {})
            return True

    def fail(
        self,
        *,
        run_id: str,
        worker_id: str,
        lease_version: int,
        error_type: str,
        error_message: str,
        retryable: bool,
        now: datetime | None = None,
    ) -> bool:
        current_time = now or datetime.now(UTC)
        with Session(self._engine) as session, session.begin():
            run = self._locked_fenced_run(session, run_id, worker_id, lease_version, current_time)
            if run is None:
                return False
            run.status = RunStatus.FAILED
            run.stage = "FAILED"
            run.error_type = error_type
            run.error_message = error_message[:2000]
            run.retryable = retryable
            run.completed_at = current_time
            run.updated_at = current_time
            self._clear_lease(run)
            self._finish_current_attempt(
                session,
                run,
                AttemptStatus.FAILED,
                current_time,
                error_type,
                error_message,
                retryable,
            )
            self._append_event(session, run, "RUN_FAILED", "FAILED", {"errorType": error_type})
            return True

    def request_cancel(self, run_id: str, now: datetime | None = None) -> AgentRun | None:
        current_time = now or datetime.now(UTC)
        with Session(self._engine) as session, session.begin():
            run = session.scalar(
                select(AgentRun).where(AgentRun.run_id == run_id).with_for_update()
            )
            if run is None:
                return None
            if run.status in {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED}:
                session.expunge(run)
                return run
            run.cancel_requested_at = run.cancel_requested_at or current_time
            run.updated_at = current_time
            if run.status == RunStatus.SUBMITTED:
                run.status = RunStatus.CANCELLED
                run.stage = "CANCELLED"
                run.completed_at = current_time
            self._append_event(session, run, "CANCEL_REQUESTED", run.stage, {})
            session.flush()
            session.expunge(run)
            return run

    def is_cancel_requested(self, run_id: str) -> bool:
        with Session(self._engine) as session:
            return bool(
                session.scalar(
                    select(AgentRun.cancel_requested_at.is_not(None)).where(
                        AgentRun.run_id == run_id
                    )
                )
            )

    def mark_cancelled(
        self,
        *,
        run_id: str,
        worker_id: str,
        lease_version: int,
        now: datetime | None = None,
    ) -> bool:
        current_time = now or datetime.now(UTC)
        with Session(self._engine) as session, session.begin():
            run = self._locked_fenced_run(session, run_id, worker_id, lease_version, current_time)
            if run is None or run.cancel_requested_at is None:
                return False
            run.status = RunStatus.CANCELLED
            run.stage = "CANCELLED"
            run.completed_at = current_time
            run.updated_at = current_time
            self._clear_lease(run)
            self._finish_current_attempt(session, run, AttemptStatus.CANCELLED, current_time)
            self._append_event(session, run, "RUN_CANCELLED", "CANCELLED", {})
            return True

    def fail_exhausted_leases(self, now: datetime | None = None) -> int:
        current_time = now or datetime.now(UTC)
        with Session(self._engine) as session, session.begin():
            runs = session.scalars(
                select(AgentRun)
                .where(
                    AgentRun.status == RunStatus.RUNNING,
                    AgentRun.lease_expires_at < current_time,
                    AgentRun.claim_count >= AgentRun.max_claims,
                )
                .with_for_update(skip_locked=True)
            ).all()
            for run in runs:
                run.status = RunStatus.FAILED
                run.stage = "FAILED"
                run.error_type = "MAX_CLAIMS_EXCEEDED"
                run.error_message = "Worker lease expired too many times"
                run.retryable = True
                run.completed_at = current_time
                run.updated_at = current_time
                self._clear_lease(run)
                self._finish_current_attempt(
                    session,
                    run,
                    AttemptStatus.LEASE_EXPIRED,
                    current_time,
                    "MAX_CLAIMS_EXCEEDED",
                    run.error_message,
                    True,
                )
                self._append_event(
                    session,
                    run,
                    "RUN_FAILED",
                    "FAILED",
                    {"errorType": "MAX_CLAIMS_EXCEEDED"},
                )
            return len(runs)

    @staticmethod
    def _fence(run_id: str, worker_id: str, lease_version: int, now: datetime) -> tuple[Any, ...]:
        return (
            AgentRun.run_id == run_id,
            AgentRun.status == RunStatus.RUNNING,
            AgentRun.lease_owner == worker_id,
            AgentRun.lease_version == lease_version,
            AgentRun.lease_expires_at >= now,
        )

    def _locked_fenced_run(
        self,
        session: Session,
        run_id: str,
        worker_id: str,
        lease_version: int,
        now: datetime,
    ) -> AgentRun | None:
        return session.scalar(
            select(AgentRun)
            .where(*self._fence(run_id, worker_id, lease_version, now))
            .with_for_update()
        )

    @staticmethod
    def _clear_lease(run: AgentRun) -> None:
        run.lease_owner = None
        run.lease_expires_at = None

    @staticmethod
    def _finish_current_attempt(
        session: Session,
        run: AgentRun,
        status: AttemptStatus,
        now: datetime,
        error_type: str | None = None,
        error_message: str | None = None,
        retryable: bool | None = None,
    ) -> None:
        attempt = session.scalar(
            select(AgentRunAttempt).where(
                AgentRunAttempt.run_id == run.run_id,
                AgentRunAttempt.attempt_number == run.claim_count,
                AgentRunAttempt.status == AttemptStatus.RUNNING,
            )
        )
        if attempt is None:
            return
        attempt.status = status
        attempt.finished_at = now
        attempt.error_type = error_type
        attempt.error_message = error_message[:2000] if error_message else None
        attempt.retryable = retryable

    @staticmethod
    def _append_event(
        session: Session,
        run: AgentRun,
        event_type: str,
        stage: str,
        summary: dict[str, Any],
    ) -> None:
        if run in session.new:
            sequence = 1
        else:
            sequence = (
                session.scalar(
                    select(func.max(AgentEvent.sequence)).where(AgentEvent.run_id == run.run_id)
                )
                or 0
            ) + 1
        session.add(
            AgentEvent(
                run_id=run.run_id,
                sequence=sequence,
                event_type=event_type,
                stage=stage,
                summary=summary,
            )
        )
