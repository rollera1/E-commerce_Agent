from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from decimal import Decimal, InvalidOperation
from threading import Lock
from typing import Any
from urllib.parse import quote

import httpx

from ecommerce_agent_ai.product_sources.models import (
    Money,
    ProductSourceName,
    SourceProduct,
    SourceProductPage,
    SourceVariant,
)

BASE_URL = "https://developers.cjdropshipping.com/api2.0/v1"
REFRESH_MARGIN = timedelta(minutes=1)


class CjApiError(RuntimeError):
    def __init__(self, code: int, request_id: str | None, retryable: bool) -> None:
        super().__init__(
            f"CJ API request failed with code {code}; requestId={request_id or 'unknown'}"
        )
        self.code = code
        self.request_id = request_id
        self.retryable = retryable


@dataclass(frozen=True)
class _Tokens:
    access_token: str
    access_expires_at: datetime
    refresh_token: str
    refresh_expires_at: datetime


class CjProductSource:
    def __init__(self, api_key: str, *, client: httpx.Client | None = None) -> None:
        if not api_key.strip():
            raise ValueError("CJ API key must not be blank")
        self._api_key = api_key
        self._client = client or httpx.Client(base_url=BASE_URL, timeout=10.0)
        self._tokens: _Tokens | None = None
        self._token_lock = Lock()

    def list_products(self, keyword: str | None, page: int, page_size: int) -> SourceProductPage:
        params: dict[str, str | int] = {"page": page, "size": page_size}
        if keyword:
            params["keyWord"] = keyword
        data = self._business_request("GET", "/product/listV2", params=params)
        content = data.get("content") or []
        products = [product for item in content for product in (item.get("productList") or [item])]
        return SourceProductPage(
            items=[self._map_product(item) for item in products],
            page=int(data.get("pageNumber", page)),
            page_size=int(data.get("pageSize", page_size)),
            total=int(data.get("totalRecords", len(products))),
        )

    def get_product(self, source_product_id: str) -> SourceProduct | None:
        try:
            data = self._business_request(
                "GET", "/product/query", params={"pid": source_product_id}
            )
        except CjApiError as error:
            if error.code in {1602001, 1602002}:
                return None
            raise
        return self._map_product(data) if data else None

    def _business_request(self, method: str, path: str, **kwargs: Any) -> dict[str, Any]:
        token = self._access_token()
        try:
            response = self._client.request(
                method, f"{BASE_URL}{path}", headers={"CJ-Access-Token": token}, **kwargs
            )
        except httpx.RequestError as error:
            raise CjApiError(-1, None, True) from error
        return self._response_data(response)

    def _access_token(self) -> str:
        with self._token_lock:
            now = datetime.now(UTC)
            if self._tokens is None:
                self._tokens = self._token_request(
                    "/authentication/getAccessToken", {"apiKey": self._api_key}
                )
            if self._tokens.access_expires_at > now + REFRESH_MARGIN:
                return self._tokens.access_token
            if self._tokens.refresh_expires_at > now + REFRESH_MARGIN:
                self._tokens = self._token_request(
                    "/authentication/refreshAccessToken",
                    {"refreshToken": self._tokens.refresh_token},
                )
            else:
                self._tokens = self._token_request(
                    "/authentication/getAccessToken", {"apiKey": self._api_key}
                )
            return self._tokens.access_token

    def _token_request(self, path: str, body: dict[str, str]) -> _Tokens:
        try:
            response = self._client.post(f"{BASE_URL}{path}", json=body)
        except httpx.RequestError as error:
            raise CjApiError(-1, None, True) from error
        data = self._response_data(response)
        try:
            return _Tokens(
                access_token=str(data["accessToken"]),
                access_expires_at=self._parse_time(data["accessTokenExpiryDate"]),
                refresh_token=str(data["refreshToken"]),
                refresh_expires_at=self._parse_time(data["refreshTokenExpiryDate"]),
            )
        except (KeyError, TypeError, ValueError) as error:
            raise CjApiError(0, None, False) from error

    @staticmethod
    def _response_data(response: httpx.Response) -> dict[str, Any]:
        try:
            payload = response.json()
        except ValueError as error:
            raise CjApiError(response.status_code, None, response.status_code >= 500) from error
        code = int(payload.get("code", response.status_code))
        succeeded = response.is_success and code == 200 and payload.get("result", True) is not False
        if not succeeded:
            retryable = response.status_code == 429 or code in {
                429,
                1600000,
                1600200,
                1600201,
                1600301,
            }
            raise CjApiError(code, payload.get("requestId"), retryable)
        data = payload.get("data")
        if not isinstance(data, dict):
            raise CjApiError(code, payload.get("requestId"), False)
        return data

    @staticmethod
    def _parse_time(value: Any) -> datetime:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        return parsed.replace(tzinfo=UTC) if parsed.tzinfo is None else parsed.astimezone(UTC)

    @staticmethod
    def _map_product(raw: dict[str, Any]) -> SourceProduct:
        product_id = str(raw.get("pid") or raw.get("id") or "").strip()
        sku = str(raw.get("productSku") or raw.get("sku") or product_id).strip()
        title = str(
            raw.get("productNameEn")
            or raw.get("nameEn")
            or raw.get("productName")
            or raw.get("name")
            or sku
        ).strip()
        if not product_id or not title:
            raise CjApiError(0, None, False)
        price = CjProductSource._money(raw.get("sellPrice") or raw.get("productPrice"))
        images = CjProductSource._images(raw)
        variants = CjProductSource._variants(raw, product_id, sku, title, price)
        return SourceProduct(
            source=ProductSourceName.CJ,
            source_product_id=product_id,
            title=title,
            description=str(raw.get("description") or raw.get("productDescription") or ""),
            images=images,
            price=price,
            variants=variants,
            source_url=f"{BASE_URL}/product/query?pid={quote(product_id)}",
            fetched_at=datetime.now(UTC),
        )

    @staticmethod
    def _money(value: Any) -> Money:
        raw_value = str(value or "0").strip()
        matched_amount = re.search(r"\d+(?:\.\d+)?", raw_value)
        if matched_amount is None:
            raise CjApiError(0, None, False)
        try:
            amount = Decimal(matched_amount.group())
        except InvalidOperation as error:
            raise CjApiError(0, None, False) from error
        return Money(amount=amount, currency="USD")

    @staticmethod
    def _images(raw: dict[str, Any]) -> list[str]:
        value = raw.get("productImageSet") or []
        images = value if isinstance(value, list) else str(value).split(",")
        primary = raw.get("bigImage") or raw.get("productImage")
        values = ([primary] if primary else []) + images
        return list(dict.fromkeys(str(item).strip() for item in values if str(item).strip()))

    @staticmethod
    def _variants(
        raw: dict[str, Any], product_id: str, sku: str, title: str, price: Money
    ) -> list[SourceVariant]:
        raw_variants = raw.get("variants") or raw.get("variantList") or []
        if not raw_variants:
            return [
                SourceVariant(
                    source_variant_id=product_id,
                    sku=sku,
                    title=title,
                    price=price,
                    inventory_available=raw.get("totalInventory"),
                )
            ]
        return [
            SourceVariant(
                source_variant_id=str(item.get("vid") or item.get("id") or item.get("variantSku")),
                sku=str(item.get("variantSku") or item.get("sku")),
                title=str(item.get("variantNameEn") or item.get("variantName") or title),
                price=CjProductSource._money(item.get("variantSellPrice") or item.get("sellPrice")),
                inventory_available=item.get("variantInventory") or item.get("inventory"),
            )
            for item in raw_variants
        ]
