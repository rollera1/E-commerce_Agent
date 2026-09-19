from datetime import UTC, datetime, timedelta
from decimal import Decimal

import httpx
import pytest

from ecommerce_agent_ai.product_sources.cj import CjApiError, CjProductSource


def future(days: int) -> str:
    return (datetime.now(UTC) + timedelta(days=days)).isoformat()


def test_authenticates_once_and_maps_product_list() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.path.endswith("/authentication/getAccessToken"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "accessToken": "access-secret",
                        "accessTokenExpiryDate": future(1),
                        "refreshToken": "refresh-secret",
                        "refreshTokenExpiryDate": future(30),
                    },
                },
            )
        assert request.headers["CJ-Access-Token"] == "access-secret"
        return httpx.Response(
            200,
            json={
                "code": 200,
                "result": True,
                "data": {
                    "pageNumber": 1,
                    "pageSize": 20,
                    "totalRecords": 1,
                    "content": [
                        {
                            "pid": "cj-1",
                            "productNameEn": "Desk Lamp",
                            "productSku": "CJ-LAMP",
                            "sellPrice": "12.50",
                            "bigImage": "https://img.example/lamp.jpg",
                        }
                    ],
                },
            },
        )

    source = CjProductSource(
        "api-secret", client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    first = source.list_products("lamp", 1, 20)
    second = source.list_products("lamp", 1, 20)

    assert first.items[0].source_product_id == "cj-1"
    assert first.items[0].price.amount == 12.50
    assert first.items[0].variants[0].sku == "CJ-LAMP"
    assert sum(r.url.path.endswith("/authentication/getAccessToken") for r in requests) == 1
    assert second.total == 1


def test_maps_current_list_v2_nested_product_contract() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/authentication/getAccessToken"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "accessToken": "token",
                        "accessTokenExpiryDate": future(1),
                        "refreshToken": "refresh",
                        "refreshTokenExpiryDate": future(30),
                    },
                },
            )
        return httpx.Response(
            200,
            json={
                "code": 200,
                "result": True,
                "data": {
                    "pageNumber": 1,
                    "pageSize": 5,
                    "totalRecords": 1,
                    "content": [
                        {
                            "productList": [
                                {
                                    "id": "current-id",
                                    "nameEn": "Current CJ Product",
                                    "sku": "CURRENT-SKU",
                                    "sellPrice": "9.99",
                                    "bigImage": "https://img.example/current.jpg",
                                }
                            ],
                            "relatedCategoryList": [],
                            "keyWord": None,
                            "keyWordOld": None,
                        }
                    ],
                },
            },
        )

    source = CjProductSource(
        "api-secret", client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    page = source.list_products(None, 1, 5)

    assert len(page.items) == 1
    assert page.items[0].source_product_id == "current-id"
    assert page.items[0].title == "Current CJ Product"


@pytest.mark.parametrize("raw_price", ["2.79 -- 4.25", "2.79-4.25"])
def test_maps_price_range_to_lowest_source_price(raw_price: str) -> None:
    money = CjProductSource._money(raw_price)
    assert money.amount == Decimal("2.79")


def test_refreshes_expired_access_token() -> None:
    auth_calls: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        auth_calls.append(request.url.path)
        if request.url.path.endswith("/authentication/getAccessToken"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "accessToken": "old",
                        "accessTokenExpiryDate": future(-1),
                        "refreshToken": "refresh",
                        "refreshTokenExpiryDate": future(30),
                    },
                },
            )
        if request.url.path.endswith("/authentication/refreshAccessToken"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "accessToken": "new",
                        "accessTokenExpiryDate": future(1),
                        "refreshToken": "refresh-2",
                        "refreshTokenExpiryDate": future(30),
                    },
                },
            )
        assert request.headers["CJ-Access-Token"] == "new"
        return httpx.Response(
            200,
            json={
                "code": 200,
                "result": True,
                "data": {"pageNumber": 1, "pageSize": 20, "totalRecords": 0, "content": []},
            },
        )

    source = CjProductSource(
        "api-secret", client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    source.list_products(None, 1, 20)

    assert any(path.endswith("/authentication/refreshAccessToken") for path in auth_calls)


@pytest.mark.parametrize(
    "status,code,retryable", [(429, 429, True), (200, 1600200, True), (200, 1600001, False)]
)
def test_classifies_cj_failures(status: int, code: int, retryable: bool) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/authentication/getAccessToken"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "accessToken": "token",
                        "accessTokenExpiryDate": future(1),
                        "refreshToken": "refresh",
                        "refreshTokenExpiryDate": future(30),
                    },
                },
            )
        return httpx.Response(status, json={"code": code, "result": False, "requestId": "req-1"})

    source = CjProductSource(
        "api-secret", client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    with pytest.raises(CjApiError) as raised:
        source.list_products(None, 1, 20)
    assert raised.value.code == code
    assert raised.value.retryable is retryable
    assert "api-secret" not in str(raised.value)
    assert "token" not in str(raised.value)


def test_returns_none_for_missing_product() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/authentication/getAccessToken"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "accessToken": "token",
                        "accessTokenExpiryDate": future(1),
                        "refreshToken": "refresh",
                        "refreshTokenExpiryDate": future(30),
                    },
                },
            )
        return httpx.Response(200, json={"code": 1602001, "result": False, "requestId": "req-2"})

    source = CjProductSource(
        "api-secret", client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    assert source.get_product("missing") is None


def test_marks_network_timeout_as_retryable() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/authentication/getAccessToken"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "accessToken": "token",
                        "accessTokenExpiryDate": future(1),
                        "refreshToken": "refresh",
                        "refreshTokenExpiryDate": future(30),
                    },
                },
            )
        raise httpx.ReadTimeout("upstream timed out", request=request)

    source = CjProductSource(
        "api-secret", client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    with pytest.raises(CjApiError) as raised:
        source.list_products(None, 1, 20)
    assert raised.value.retryable is True


def test_rejects_product_without_source_identifier() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/authentication/getAccessToken"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "accessToken": "token",
                        "accessTokenExpiryDate": future(1),
                        "refreshToken": "refresh",
                        "refreshTokenExpiryDate": future(30),
                    },
                },
            )
        return httpx.Response(
            200,
            json={
                "code": 200,
                "result": True,
                "data": {"pageNumber": 1, "pageSize": 20, "content": [{"productNameEn": "No ID"}]},
            },
        )

    source = CjProductSource(
        "api-secret", client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    with pytest.raises(CjApiError) as raised:
        source.list_products(None, 1, 20)
    assert raised.value.retryable is False
