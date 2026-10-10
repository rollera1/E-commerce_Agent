import os
from collections.abc import Iterator
from contextlib import contextmanager

from langgraph.checkpoint.postgres import PostgresSaver

from ecommerce_agent_ai.runtime.config import AgentRuntimeSettings


def checkpoint_connection_string(settings: AgentRuntimeSettings | None = None) -> str:
    """把 SQLAlchemy URL 转为 Psycopg 可识别的 PostgreSQL URL。"""
    runtime_settings = settings or AgentRuntimeSettings.from_env()
    return runtime_settings.database_url.replace("postgresql+psycopg://", "postgresql://", 1)


def setup_checkpoint_tables(settings: AgentRuntimeSettings | None = None) -> None:
    """由官方 PostgresSaver 创建并维护 checkpoint 表。"""
    os.environ.setdefault("LANGGRAPH_STRICT_MSGPACK", "true")
    with PostgresSaver.from_conn_string(checkpoint_connection_string(settings)) as saver:
        saver.setup()


@contextmanager
def open_checkpoint_saver(
    settings: AgentRuntimeSettings | None = None,
) -> Iterator[PostgresSaver]:
    """打开由 Worker 独占生命周期的 PostgreSQL checkpoint 存储。"""
    os.environ.setdefault("LANGGRAPH_STRICT_MSGPACK", "true")
    with PostgresSaver.from_conn_string(checkpoint_connection_string(settings)) as saver:
        yield saver
