from fastapi.testclient import TestClient

from ecommerce_agent_ai.main import app

client = TestClient(app)


def test_health_returns_service_status() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "ai-python",
        "version": "0.1.0",
    }
