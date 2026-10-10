import logging
import os
import socket
import threading
import time
from collections.abc import Callable
from uuid import uuid4

from ecommerce_agent_ai.runtime.config import AgentRuntimeSettings
from ecommerce_agent_ai.runtime.database import create_runtime_engine
from ecommerce_agent_ai.runtime.executor import (
    CopySeoRunExecutor,
    ExecutionResult,
    RunExecutionError,
)
from ecommerce_agent_ai.runtime.repository import AgentRuntimeRepository, ClaimedRun

logger = logging.getLogger(__name__)


class AgentWorker:
    """用 PostgreSQL 租约串行领取并执行 Agent 任务。"""

    def __init__(
        self,
        repository: AgentRuntimeRepository,
        execute: Callable[[ClaimedRun], ExecutionResult],
        settings: AgentRuntimeSettings,
        worker_id: str,
    ) -> None:
        self._repository = repository
        self._execute = execute
        self._settings = settings
        self._worker_id = worker_id

    def run_once(self) -> bool:
        self._repository.fail_exhausted_leases()
        claimed = self._repository.claim_next(
            worker_id=self._worker_id,
            lease_seconds=self._settings.lease_seconds,
        )
        if claimed is None:
            return False
        if self._repository.is_cancel_requested(claimed.run_id):
            self._repository.mark_cancelled(
                run_id=claimed.run_id,
                worker_id=self._worker_id,
                lease_version=claimed.lease_version,
            )
            return True

        heartbeat = _LeaseHeartbeat(self._repository, claimed, self._settings)
        heartbeat.start()
        self._repository.update_stage(
            run_id=claimed.run_id,
            worker_id=self._worker_id,
            lease_version=claimed.lease_version,
            stage="EXECUTING",
        )
        try:
            result = self._execute(claimed)
            if heartbeat.lost_lease:
                return True
            if self._repository.is_cancel_requested(claimed.run_id):
                self._repository.mark_cancelled(
                    run_id=claimed.run_id,
                    worker_id=self._worker_id,
                    lease_version=claimed.lease_version,
                )
                return True
            self._repository.complete(
                run_id=claimed.run_id,
                worker_id=self._worker_id,
                lease_version=claimed.lease_version,
                artifact_type=result.artifact_type,
                payload=result.payload,
                agent_version=result.agent_version,
                prompt_version=result.prompt_version,
                model=result.model,
            )
        except RunExecutionError as error:
            self._finish_failure(claimed, error.error_type, error.retryable)
        except Exception:
            logger.error("Agent run failed unexpectedly: %s", claimed.run_id)
            self._finish_failure(claimed, "INTERNAL_AGENT_ERROR", False)
        finally:
            heartbeat.stop()
        return True

    def _finish_failure(self, claimed: ClaimedRun, error_type: str, retryable: bool) -> None:
        if self._repository.is_cancel_requested(claimed.run_id):
            self._repository.mark_cancelled(
                run_id=claimed.run_id,
                worker_id=self._worker_id,
                lease_version=claimed.lease_version,
            )
            return
        self._repository.fail(
            run_id=claimed.run_id,
            worker_id=self._worker_id,
            lease_version=claimed.lease_version,
            error_type=error_type,
            error_message="Agent execution failed",
            retryable=retryable,
        )


class _LeaseHeartbeat:
    def __init__(
        self,
        repository: AgentRuntimeRepository,
        claimed: ClaimedRun,
        settings: AgentRuntimeSettings,
    ) -> None:
        self._repository = repository
        self._claimed = claimed
        self._settings = settings
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self.lost_lease = False

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._thread.join(timeout=1)

    def _run(self) -> None:
        while not self._stop.wait(self._settings.heartbeat_seconds):
            renewed = self._repository.renew_lease(
                run_id=self._claimed.run_id,
                worker_id=self._claimed.worker_id,
                lease_version=self._claimed.lease_version,
                lease_seconds=self._settings.lease_seconds,
            )
            if not renewed:
                self.lost_lease = True
                return


def main() -> None:
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
    settings = AgentRuntimeSettings.from_env()
    repository = AgentRuntimeRepository(create_runtime_engine(settings), settings.max_claims)
    executor = CopySeoRunExecutor()
    worker_id = f"{socket.gethostname()}:{os.getpid()}:{str(uuid4())[:8]}"
    worker = AgentWorker(repository, executor.execute, settings, worker_id)
    logger.info("Agent worker started: %s", worker_id)
    try:
        while True:
            if not worker.run_once():
                time.sleep(settings.poll_interval_seconds)
    except KeyboardInterrupt:
        logger.info("Agent worker stopped")


if __name__ == "__main__":
    main()
