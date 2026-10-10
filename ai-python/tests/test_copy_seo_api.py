import pytest
from fastapi.testclient import TestClient

from ecommerce_agent_ai.agents.copy_seo.errors import CopySeoAgentError
from ecommerce_agent_ai.agents.copy_seo.models import AgentOutcome, AgentTrace
from ecommerce_agent_ai.main import app

client = TestClient(app)


def test_generates_structured_copy_pending_merchant_review() -> None:
    response = client.post(
        "/internal/agents/copy-seo/generations",
        json={
            "sourceProductId": "fake-desk-lamp-001",
            "targetMarket": "US",
            "language": "en",
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["sourceProductId"] == "fake-desk-lamp-001"
    assert payload["status"] == "SUCCEEDED"
    assert payload["reviewStatus"] == "PENDING_REVIEW"
    assert payload["content"] == {
        "title": "Adjustable LED Desk Lamp for Focused Workspaces",
        "bulletPoints": [
            "Adjustable lighting for focused desk work",
            "Compact profile suited to home-office spaces",
            "Dimmable LED light for everyday use",
        ],
        "description": (
            "Create a comfortable home-office setup with this compact adjustable "
            "LED desk lamp. Its dimmable light helps tailor your workspace for focused "
            "tasks while the space-conscious design keeps your desk organized."
        ),
        "seoTitle": "Adjustable LED Desk Lamp | Home Office Lighting",
        "metaDescription": (
            "Shop a compact adjustable LED desk lamp with dimmable lighting for "
            "focused work and organized home-office spaces."
        ),
        "tags": ["desk lamp", "LED lighting", "home office"],
    }
    assert payload["execution"]["agent"] == "COPY_SEO"
    assert payload["execution"]["agentVersion"] == "copy-seo-fake-v2"
    assert payload["execution"]["promptVersion"] == "copy-seo-react-v1"
    assert payload["execution"]["model"] == "fake-deterministic"
    assert payload["trace"]["outcome"] == "COMPLETED"
    assert payload["trace"]["nextAction"] == "MERCHANT_REVIEW"
    assert payload["trace"]["modelAttempts"] == 1
    assert [call["tool"] for call in payload["trace"]["toolCalls"]] == [
        "get_product_facts",
        "validate_copy_seo",
    ]
    assert "apiKey" not in response.text


def test_each_generation_has_a_unique_trace_id() -> None:
    request = {
        "sourceProductId": "fake-desk-lamp-001",
        "targetMarket": "US",
        "language": "en",
    }

    first = client.post("/internal/agents/copy-seo/generations", json=request)
    second = client.post("/internal/agents/copy-seo/generations", json=request)

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["generationId"] != second.json()["generationId"]


def test_does_not_generate_for_an_unknown_source_product() -> None:
    response = client.post(
        "/internal/agents/copy-seo/generations",
        json={
            "sourceProductId": "missing-product",
            "targetMarket": "US",
            "language": "en",
        },
    )

    assert response.status_code == 404
    assert response.json()["detail"]["errorCode"] == "SOURCE_PRODUCT_NOT_FOUND"


def test_rejects_an_invalid_generation_request_before_agent_execution() -> None:
    response = client.post(
        "/internal/agents/copy-seo/generations",
        json={"sourceProductId": "", "targetMarket": "", "language": "english"},
    )

    assert response.status_code == 422


def test_returns_bounded_agent_trace_when_content_requires_human_review(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class NoncompliantAgent:
        def generate(self, *args, **kwargs):
            del args, kwargs
            trace = AgentTrace(
                current_step="AGENT_REVIEW",
                outcome=AgentOutcome.NEEDS_HUMAN_INPUT,
                model_attempts=3,
                revision_count=1,
                validation_issues=["UNSUPPORTED_CLAIM:lifetime warranty"],
                next_action="HUMAN_CONTENT_REVIEW",
            )
            raise CopySeoAgentError("CONTENT_REQUIRES_HUMAN_REVIEW", False, trace=trace)

    monkeypatch.setattr(
        "ecommerce_agent_ai.agents.copy_seo.router.copy_seo_agent",
        NoncompliantAgent(),
    )

    response = client.post(
        "/internal/agents/copy-seo/generations",
        json={
            "sourceProductId": "fake-desk-lamp-001",
            "targetMarket": "US",
            "language": "en",
        },
    )

    assert response.status_code == 502
    detail = response.json()["detail"]
    assert detail["errorCode"] == "CONTENT_REQUIRES_HUMAN_REVIEW"
    assert detail["retryable"] is False
    assert detail["nextAction"] == "HUMAN_CONTENT_REVIEW"
    assert detail["trace"]["modelAttempts"] == 3
    assert detail["trace"]["revisionCount"] == 1
    assert "apiKey" not in response.text
