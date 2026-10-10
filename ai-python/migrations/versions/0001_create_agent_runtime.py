"""创建 Agent 持久化运行时表。"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import JSONB

JSON_DOCUMENT = sa.JSON().with_variant(JSONB(), "postgresql")

revision: str = "0001_agent_runtime"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "agent_run",
        sa.Column("run_id", sa.String(36), primary_key=True),
        sa.Column("idempotency_key", sa.String(200), nullable=False, unique=True),
        sa.Column("agent_type", sa.String(50), nullable=False),
        sa.Column("business_object_type", sa.String(50), nullable=False),
        sa.Column("business_object_id", sa.String(200), nullable=False),
        sa.Column("status", sa.String(30), nullable=False),
        sa.Column("stage", sa.String(80), nullable=False),
        sa.Column("request_payload", JSON_DOCUMENT, nullable=False),
        sa.Column("agent_version", sa.String(100)),
        sa.Column("prompt_version", sa.String(100)),
        sa.Column("model", sa.String(150)),
        sa.Column("claim_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("max_claims", sa.Integer(), nullable=False, server_default="3"),
        sa.Column("lease_owner", sa.String(200)),
        sa.Column("lease_expires_at", sa.DateTime(timezone=True)),
        sa.Column("lease_version", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("cancel_requested_at", sa.DateTime(timezone=True)),
        sa.Column("available_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True)),
        sa.Column("completed_at", sa.DateTime(timezone=True)),
        sa.Column("error_type", sa.String(100)),
        sa.Column("error_message", sa.Text()),
        sa.Column("retryable", sa.Boolean()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_agent_run_claim", "agent_run", ["status", "available_at", "created_at"])
    op.create_index(
        "ix_agent_run_business_object",
        "agent_run",
        ["business_object_type", "business_object_id"],
    )
    op.create_table(
        "agent_run_attempt",
        sa.Column(
            "attempt_id",
            sa.BigInteger().with_variant(sa.Integer(), "sqlite"),
            primary_key=True,
            autoincrement=True,
        ),
        sa.Column(
            "run_id",
            sa.String(36),
            sa.ForeignKey("agent_run.run_id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("attempt_number", sa.Integer(), nullable=False),
        sa.Column("worker_id", sa.String(200), nullable=False),
        sa.Column("lease_version", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(30), nullable=False),
        sa.Column("error_type", sa.String(100)),
        sa.Column("error_message", sa.Text()),
        sa.Column("retryable", sa.Boolean()),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("finished_at", sa.DateTime(timezone=True)),
        sa.UniqueConstraint("run_id", "attempt_number"),
    )
    op.create_index("ix_agent_run_attempt_run_id", "agent_run_attempt", ["run_id"])
    op.create_table(
        "agent_event",
        sa.Column(
            "event_id",
            sa.BigInteger().with_variant(sa.Integer(), "sqlite"),
            primary_key=True,
            autoincrement=True,
        ),
        sa.Column(
            "run_id",
            sa.String(36),
            sa.ForeignKey("agent_run.run_id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("sequence", sa.Integer(), nullable=False),
        sa.Column("event_type", sa.String(80), nullable=False),
        sa.Column("stage", sa.String(80), nullable=False),
        sa.Column("summary", JSON_DOCUMENT, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("run_id", "sequence"),
    )
    op.create_index("ix_agent_event_run_id", "agent_event", ["run_id"])
    op.create_table(
        "agent_artifact",
        sa.Column("artifact_id", sa.String(36), primary_key=True),
        sa.Column(
            "run_id",
            sa.String(36),
            sa.ForeignKey("agent_run.run_id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("artifact_type", sa.String(80), nullable=False),
        sa.Column("version", sa.Integer(), nullable=False, server_default="1"),
        sa.Column("payload", JSON_DOCUMENT, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("run_id", "artifact_type", "version"),
    )
    op.create_index("ix_agent_artifact_run_id", "agent_artifact", ["run_id"])


def downgrade() -> None:
    op.drop_table("agent_artifact")
    op.drop_table("agent_event")
    op.drop_table("agent_run_attempt")
    op.drop_table("agent_run")
