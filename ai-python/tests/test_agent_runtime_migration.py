from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, inspect


def test_agent_runtime_migration_creates_only_owned_tables(
    tmp_path: Path,
    monkeypatch,
) -> None:
    monkeypatch.delenv("AGENT_DATABASE_URL", raising=False)
    project_dir = Path(__file__).parents[1]
    config = Config(project_dir / "alembic.ini")
    database_path = tmp_path / "runtime.db"
    config.set_main_option("sqlalchemy.url", f"sqlite:///{database_path.as_posix()}")

    command.upgrade(config, "head")

    tables = set(inspect(create_engine(f"sqlite:///{database_path.as_posix()}")).get_table_names())
    assert {
        "agent_run",
        "agent_run_attempt",
        "agent_event",
        "agent_artifact",
        "alembic_version",
    } == tables
