import pytest
from fastapi.testclient import TestClient

from ecommerce_agent_ai.agents.product_selection.errors import ProductSelectionAgentError
from ecommerce_agent_ai.main import app

client = TestClient(app)


def test_searches_candidate_products_with_the_selection_agent() -> None:
    response = client.post(
        "/internal/agents/product-selection/searches",
        json={"query": "desk lamp", "limit": 10},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["query"] == "desk lamp"
    assert payload["matchedQuery"] == "desk lamp"
    assert payload["outcome"] == "CANDIDATES_FOUND"
    assert payload["candidates"][0]["sourceProductId"] == "fake-desk-lamp-001"
    assert payload["trace"]["searchCount"] == 1
    assert payload["trace"]["modelAttempts"] == 0
    assert payload["trace"]["nextAction"] == "MERCHANT_SELECTION"


@pytest.mark.parametrize(
    "body",
    [
        {"query": "", "limit": 10},
        {"query": "lamp", "limit": 0},
        {"query": "lamp", "limit": 101},
    ],
)
def test_rejects_invalid_selection_requests(body: dict[str, object]) -> None:
    response = client.post("/internal/agents/product-selection/searches", json=body)

    assert response.status_code == 422


def test_maps_selection_agent_failures_without_exposing_remote_details(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FailingAgent:
        def select(self, query: str, limit: int):
            del query, limit
            raise ProductSelectionAgentError("PRODUCT_SOURCE_UNAVAILABLE", True)

    monkeypatch.setattr(
        "ecommerce_agent_ai.agents.product_selection.router.product_selection_agent",
        FailingAgent(),
    )

    response = client.post(
        "/internal/agents/product-selection/searches",
        json={"query": "pet leash", "limit": 10},
    )

    assert response.status_code == 502
    assert response.json()["detail"] == {
        "errorCode": "PRODUCT_SOURCE_UNAVAILABLE",
        "message": "选品 Agent 执行失败",
        "retryable": True,
    }
