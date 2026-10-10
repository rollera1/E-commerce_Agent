import os
from dataclasses import dataclass


@dataclass(frozen=True)
class AgentRuntimeSettings:
    """从环境变量读取 Agent 运行时参数。"""

    database_url: str
    lease_seconds: int = 120
    heartbeat_seconds: int = 30
    max_claims: int = 3
    poll_interval_seconds: float = 1.0

    @classmethod
    def from_env(cls) -> "AgentRuntimeSettings":
        settings = cls(
            database_url=os.getenv(
                "AGENT_DATABASE_URL",
                "postgresql+psycopg://agent_runtime:local_agent_password@127.0.0.1:5433/agent_runtime",
            ),
            lease_seconds=_positive_int("AGENT_LEASE_SECONDS", 120),
            heartbeat_seconds=_positive_int("AGENT_HEARTBEAT_SECONDS", 30),
            max_claims=_positive_int("AGENT_MAX_CLAIMS", 3),
            poll_interval_seconds=_positive_float("AGENT_POLL_INTERVAL_SECONDS", 1.0),
        )
        if settings.heartbeat_seconds >= settings.lease_seconds:
            raise RuntimeError("AGENT_HEARTBEAT_SECONDS 必须小于 AGENT_LEASE_SECONDS")
        return settings


def _positive_int(name: str, default: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError as error:
        raise RuntimeError(f"{name} 必须是整数") from error
    if value <= 0:
        raise RuntimeError(f"{name} 必须大于 0")
    return value


def _positive_float(name: str, default: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
    except ValueError as error:
        raise RuntimeError(f"{name} 必须是数字") from error
    if value <= 0:
        raise RuntimeError(f"{name} 必须大于 0")
    return value
