from datetime import UTC, datetime, timedelta
from decimal import Decimal

import httpx
import pytest

from ecommerce_agent_ai.product_sources.cj import CjApiError, CjProductSource
from ecommerce_agent_ai.product_sources.cj_mapper import CjProductMapper


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
        if request.url.path.endswith("/product/query"):
            return httpx.Response(
                200,
                json={
                    "code": 200,
                    "result": True,
                    "data": {
                        "pid": "cj-1",
                        "productNameEn": "Desk Lamp",
                        "description": "Adjustable lamp",
                        "sellPrice": "12.50",
                        "bigImage": "https://img.example/lamp.jpg",
                        "variants": [
                            {"vid": "v1", "variantSku": "CJ-LAMP", "variantSellPrice": "12.50"}
                        ],
                    },
                },
            )
        assert request.url.params["startWarehouseInventory"] == "1"
        assert request.url.params["features"] == "enable_description"
        assert request.url.params["zonePlatform"] == "shopify"
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
                            "warehouseInventoryNum": 8,
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
    assert first.items[0].inventory_total == 8
    assert first.items[0].variants[0].inventory_available is None
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
    money = CjProductMapper.money(raw_price)
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
    assert source.list_products(None, 1, 20).items == []


@pytest.mark.parametrize("inventory", [None, 0, 7])
def test_maps_list_inventory_without_converting_unknown_to_zero(inventory) -> None:
    from ecommerce_agent_ai.product_sources.cj_mapper import CjProductMapper

    product = CjProductMapper("https://example.invalid").map_product(
        {
            "pid": "product-1",
            "productNameEn": "Lamp",
            "sellPrice": "1",
            "totalInventory": inventory,
        }
    )
    assert product.variants == []
    assert product.inventory_total is None


def test_warehouse_summary_is_not_variant_available_stock() -> None:
    from ecommerce_agent_ai.product_sources.cj_mapper import CjProductMapper

    product = CjProductMapper("https://example.invalid").map_product(
        {
            "pid": "product-1",
            "productNameEn": "Lamp",
            "sellPrice": "1",
            "warehouseInventoryNum": 100,
        }
    )
    assert product.variants == []
    assert product.inventory_total == 100


@pytest.mark.parametrize(
    "value,expected",
    [(None, None), (0, 0), (12, 12), (-1, None), (True, None), ("12", None), (1.5, None)],
)
def test_summary_inventory_accepts_only_explicit_nonnegative_integers(value, expected) -> None:
    from ecommerce_agent_ai.product_sources.cj_mapper import CjProductMapper

    assert CjProductMapper.inventory_total(value) == expected


def test_list_skips_zero_and_unknown_inventory_before_details_and_never_fetches_variant_stock() -> (
    None
):
    source = CjProductSource("test-key", client=httpx.Client())
    calls = []

    def request(method, path, **kwargs):
        calls.append((method, path, kwargs))
        if path.endswith("listV2"):
            return {
                "totalRecords": 3,
                "content": [
                    {
                        "productList": [
                            {
                                "id": "zero",
                                "nameEn": "Lamp",
                                "sellPrice": "1",
                                "warehouseInventoryNum": 0,
                            },
                            {"id": "unknown", "nameEn": "Lamp", "sellPrice": "1"},
                            {
                                "id": "good",
                                "nameEn": "Lamp",
                                "sellPrice": "1",
                                "warehouseInventoryNum": 9,
                            },
                        ]
                    }
                ],
            }
        assert path == "/product/query" and kwargs["params"] == {"pid": "good"}
        return {
            "pid": "good",
            "productNameEn": "Lamp",
            "description": "Source lamp facts",
            "sellPrice": "1",
            "productImageSet": '["https://example.invalid/lamp.jpg"]',
            "variants": [{"vid": "real-vid", "variantSku": "sku", "variantSellPrice": "1"}],
        }

    source._request = request
    page = source.list_products("lamp", 1, 20)
    assert len(page.items) == 1 and page.items[0].inventory_total == 9
    assert page.items[0].variants[0].source_variant_id == "real-vid"
    assert page.items[0].variants[0].inventory_available is None
    assert page.items[0].images == ["https://example.invalid/lamp.jpg"]
    assert len(calls) == 2


@pytest.mark.parametrize(
    "rows,expected",
    [
        ([{"cjInventory": 0, "factoryInventory": 100}], 0),
        ([{"cjInventory": 3}, {"cjInventory": 4}], 7),
        ([{"cjInventory": 3}, {}], None),
        ([], None),
    ],
)
def test_stock_mapping_is_variant_specific_and_conservative(rows, expected) -> None:
    from ecommerce_agent_ai.product_sources.cj_mapper import CjProductMapper

    mapper = CjProductMapper("https://example.invalid")
    product = mapper.map_product(
        {
            "pid": "product-1",
            "productNameEn": "Lamp",
            "sellPrice": "1",
            "variants": [
                {"vid": "v1", "variantSku": "sku1", "variantSellPrice": "1"},
                {"vid": "v2", "variantSku": "sku2", "variantSellPrice": "1"},
            ],
        }
    )
    mapped = mapper.with_inventory(
        product, {"variantInventories": [{"vid": "v1", "inventory": rows}]}
    )
    assert mapped.variants[0].inventory_available == expected
    assert mapped.variants[1].inventory_available is None


@pytest.mark.parametrize("stock_status", [200, 429])
def test_details_fetch_real_stock_and_stock_failure_preserves_copy_context(stock_status) -> None:
    calls = []

    def handler(request):
        calls.append(request.url.path)
        if request.url.path.endswith("/authentication/getAccessToken"):
            data = {
                "accessToken": "test-token",
                "accessTokenExpiryDate": future(1),
                "refreshToken": "test-refresh",
                "refreshTokenExpiryDate": future(30),
            }
        elif request.url.path.endswith("/product/query"):
            data = {
                "pid": "p1",
                "productNameEn": "Lamp",
                "sellPrice": "1",
                "variants": [
                    {"vid": "v1", "variantSku": "sku", "variantSellPrice": "1", "inventory": 99}
                ],
            }
        else:
            assert request.url.path.endswith("/stock/getInventoryByPid")
            assert request.url.params["pid"] == "p1"
            if stock_status == 429:
                return httpx.Response(429, json={"code": 429, "result": False})
            data = {"variantInventories": [{"vid": "v1", "inventory": [{"cjInventory": 8}]}]}
        return httpx.Response(200, json={"code": 200, "result": True, "data": data})

    source = CjProductSource(
        "test-key", client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    product = source.get_product("p1")
    assert product is not None
    assert product.title == "Lamp"
    assert product.variants[0].inventory_available == (8 if stock_status == 200 else None)
    assert len(calls) == 3
