from alembic import command
from alembic.config import Config

from ecommerce_agent_ai.runtime.checkpoints import setup_checkpoint_tables


def main() -> None:
    """升级业务运行时表，并初始化官方 checkpoint 表。"""
    command.upgrade(Config("alembic.ini"), "head")
    setup_checkpoint_tables()


if __name__ == "__main__":
    main()
