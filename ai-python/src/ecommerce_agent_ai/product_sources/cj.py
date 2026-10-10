from typing import Any

import httpx

from ecommerce_agent_ai.product_sources.cj_auth import CjTokenProvider
from ecommerce_agent_ai.product_sources.cj_error import CjApiError
from ecommerce_agent_ai.product_sources.cj_mapper import CjProductMapper
from ecommerce_agent_ai.product_sources.cj_transport import CjTransport
from ecommerce_agent_ai.product_sources.models import SourceProduct, SourceProductPage

BASE_URL = "https://developers.cjdropshipping.com/api2.0/v1"


class CjProductSource:
    """CJ 商品来源适配器，对外只暴露统一商品查询接口。"""

    def __init__(self, api_key: str, *, client: httpx.Client | None = None) -> None:
        http_client = client or httpx.Client(base_url=BASE_URL, timeout=10.0)
        self._transport = CjTransport(
            http_client, BASE_URL, min_interval=1.05 if client is None else 0
        )
        self._token_provider = CjTokenProvider(api_key, self._transport)
        self._mapper = CjProductMapper(BASE_URL)

    def list_products(self, keyword: str | None, page: int, page_size: int) -> SourceProductPage:
        params: dict[str, str | int] = {"page": page, "size": page_size}
        if keyword:
            params["keyWord"] = keyword
            params["startWarehouseInventory"] = 1
            params["features"] = "enable_description"
            params["zonePlatform"] = "shopify"
        data = self._request("GET", "/product/listV2", params=params)
        content = data.get("content") or []
        products = [product for item in content for product in (item.get("productList") or [item])]
        items: list[SourceProduct] = []
        for item in products:
            try:
                summary = self._mapper.map_product(item)
            except (CjApiError, ValueError):
                continue
            if keyword:
                if summary.inventory_total is None or summary.inventory_total <= 0:
                    continue
                # 候选补齐真实详情，不在搜索阶段查询变体库存，也不翻页凑数。
                try:
                    detail = self._get_detail(summary.source_product_id)
                except ValueError:
                    continue
                except CjApiError as error:
                    if error.code == 0 and not error.retryable:
                        continue
                    raise
                if detail is None:
                    continue
                if detail.source_product_id != summary.source_product_id:
                    continue
                summary = detail.model_copy(update={"inventory_total": summary.inventory_total})
            items.append(summary)
        return SourceProductPage(
            items=items,
            page=int(data.get("pageNumber", page)),
            page_size=int(data.get("pageSize", page_size)),
            total=int(data.get("totalRecords", len(products))),
        )

    def get_product(self, source_product_id: str) -> SourceProduct | None:
        product = self._get_detail(source_product_id)
        if product is None:
            return None
        try:
            stock = self._request(
                "GET", "/product/stock/getInventoryByPid", params={"pid": source_product_id}
            )
        except CjApiError:
            # 库存接口失败不阻塞文案生成，但必须清除旧库存，禁止误上架。
            stock = {}
        return self._mapper.with_inventory(product, stock)

    def _get_detail(self, source_product_id: str) -> SourceProduct | None:
        """只补齐来源资料；真实变体库存仍由上架前的独立查询校验。"""
        try:
            data = self._request("GET", "/product/query", params={"pid": source_product_id})
        except CjApiError as error:
            if error.code in {1602001, 1602002}:
                return None
            raise
        if not data:
            return None
        return self._mapper.map_product(data)

    def _request(self, method: str, path: str, **kwargs: Any) -> dict[str, Any]:
        """每次业务请求取得有效令牌，不向调用方暴露凭据。"""
        token = self._token_provider.access_token()
        headers = {**kwargs.pop("headers", {}), "CJ-Access-Token": token}
        return self._transport.request(method, path, headers=headers, **kwargs)


__all__ = ["CjApiError", "CjProductSource"]
