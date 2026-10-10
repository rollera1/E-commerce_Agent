from sqlalchemy import Engine, create_engine

from ecommerce_agent_ai.runtime.config import AgentRuntimeSettings


def create_runtime_engine(settings: AgentRuntimeSettings | None = None) -> Engine:
    """创建 API 与 Worker 共用的同步数据库引擎。"""
    runtime_settings = settings or AgentRuntimeSettings.from_env()
    return create_engine(runtime_settings.database_url, pool_pre_ping=True)
