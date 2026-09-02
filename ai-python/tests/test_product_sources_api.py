from fastapi.testclient import TestClient

from ecommerce_agent_ai.main import app

client = TestClient(app)


def test_lists_standardized_source_products() -> None:
    response = client.get(
        "/internal/product-sources/products",
        params={"keyword": "desk", "page": 1, "pageSize": 20},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["page"] == 1
    assert payload["pageSize"] == 20
    assert payload["total"] == 1
    assert payload["items"][0] == {
        "source": "FAKE",
        "sourceProductId": "fake-desk-lamp-001",
        "title": "Adjustable LED Desk Lamp",
        "description": "A compact dimmable desk lamp for home offices.",
        "images": ["https://example.invalid/products/desk-lamp-001.jpg"],
        "price": {"amount": "29.90", "currency": "USD"},
        "variants": [
            {
                "sourceVariantId": "fake-desk-lamp-black",
                "sku": "FAKE-LAMP-BLK",
                "title": "Black",
                "price": {"amount": "29.90", "currency": "USD"},
                "inventoryAvailable": 50,
            }
        ],
        "sourceUrl": "https://example.invalid/products/fake-desk-lamp-001",
        "fetchedAt": "2026-09-02T00:00:00Z",
    }


def test_filters_products_by_keyword() -> None:
    response = client.get(
        "/internal/product-sources/products",
        params={"keyword": "not-found", "page": 1, "pageSize": 20},
    )

    assert response.status_code == 200
    assert response.json() == {"items": [], "page": 1, "pageSize": 20, "total": 0}


def test_gets_source_product_detail() -> None:
    response = client.get("/internal/product-sources/products/fake-desk-lamp-001")

    assert response.status_code == 200
    assert response.json()["sourceProductId"] == "fake-desk-lamp-001"
    assert response.json()["variants"][0]["sku"] == "FAKE-LAMP-BLK"


def test_returns_safe_not_found_error() -> None:
    response = client.get("/internal/product-sources/products/missing-product")

    assert response.status_code == 404
    assert response.json() == {
        "detail": {
            "errorCode": "SOURCE_PRODUCT_NOT_FOUND",
            "message": "Source product was not found",
        }
    }
