from fastapi.testclient import TestClient

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
    assert payload["execution"]["agentVersion"] == "fake-v1"
    assert payload["execution"]["promptVersion"] == "copy-seo-v1"
    assert payload["execution"]["model"] == "fake-deterministic"
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
