from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool

from ecommerce_agent_ai.main import app
from ecommerce_agent_ai.runtime.models import Base
from ecommerce_agent_ai.runtime.repository import AgentRuntimeRepository
from ecommerce_agent_ai.runtime.router import get_runtime_repository


def test_runtime_api_is_idempotent_queryable_and_cancellable() -> None:
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    repository = AgentRuntimeRepository(engine)
    app.dependency_overrides[get_runtime_repository] = lambda: repository
    client = TestClient(app)
    request = {
        "idempotencyKey": "selection-1:copy-seo:v1",
        "agentType": "COPY_SEO",
        "businessObjectType": "PRODUCT_SELECTION",
        "businessObjectId": "selection-1",
        "input": {
            "sourceProductId": "fake-desk-lamp-001",
            "targetMarket": "US",
            "language": "en",
        },
    }
    try:
        first = client.post("/internal/agent-runs", json=request)
        second = client.post("/internal/agent-runs", json=request)
        assert first.status_code == 202
        assert second.status_code == 202
        assert second.json()["runId"] == first.json()["runId"]
        assert first.json()["status"] == "SUBMITTED"

        run_id = first.json()["runId"]
        fetched = client.get(f"/internal/agent-runs/{run_id}")
        assert fetched.status_code == 200
        assert fetched.json()["output"] is None

        cancelled = client.post(f"/internal/agent-runs/{run_id}/cancel")
        assert cancelled.status_code == 200
        assert cancelled.json()["status"] == "CANCELLED"
        assert cancelled.json()["cancelRequested"] is True
    finally:
        app.dependency_overrides.clear()


def test_runtime_api_rejects_unknown_agent_and_hides_unknown_runs() -> None:
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    repository = AgentRuntimeRepository(engine)
    app.dependency_overrides[get_runtime_repository] = lambda: repository
    client = TestClient(app)
    try:
        invalid = client.post(
            "/internal/agent-runs",
            json={
                "idempotencyKey": "bad-agent",
                "agentType": "PRODUCT_SELECTION",
                "businessObjectType": "PRODUCT_SELECTION",
                "businessObjectId": "selection-1",
                "input": {},
            },
        )
        assert invalid.status_code == 422

        missing = client.get("/internal/agent-runs/missing")
        assert missing.status_code == 404
        assert missing.json()["detail"]["errorCode"] == "RUN_NOT_FOUND"
    finally:
        app.dependency_overrides.clear()
