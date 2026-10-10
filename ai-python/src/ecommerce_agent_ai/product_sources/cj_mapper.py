import json
import re
from datetime import UTC, datetime
from decimal import Decimal, InvalidOperation
from typing import Any
from urllib.parse import quote

from ecommerce_agent_ai.product_sources.cj_error import CjApiError
from ecommerce_agent_ai.product_sources.models import (
    Money,
    ProductSourceName,
    SourceProduct,
    SourceVariant,
)


class CjProductMapper:
    """将 CJ 原始商品数据转换为项目统一的商品领域对象。"""

    def __init__(self, base_url: str) -> None:
        self._base_url = base_url.rstrip("/")

    def map_product(self, raw: dict[str, Any]) -> SourceProduct:
        product_id = str(raw.get("pid") or raw.get("id") or "").strip()
        title = str(
            raw.get("productNameEn")
            or raw.get("nameEn")
            or raw.get("productName")
            or raw.get("name")
            or ""
        ).strip()
        if not product_id or not title:
            raise CjApiError(0, None, False)
        price = self.money(raw.get("sellPrice") or raw.get("productPrice"))
        return SourceProduct(
            source=ProductSourceName.CJ,
            source_product_id=product_id,
            title=title,
            description=str(raw.get("description") or raw.get("productDescription") or ""),
            images=self._images(raw),
            price=price,
            variants=self._variants(raw, title),
            source_url=f"{self._base_url}/product/query?pid={quote(product_id)}",
            fetched_at=datetime.now(UTC),
            inventory_total=self.inventory_total(raw.get("warehouseInventoryNum")),
        )

    @staticmethod
    def inventory_total(value: Any) -> int | None:
        """只接受 CJ 明确返回的非负整数；不从变体推导商品汇总库存。"""
        return value if type(value) is int and value >= 0 else None

    @staticmethod
    def money(value: Any) -> Money:
        """将 CJ 价格或价格区间转换为最低美元金额。"""
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
    def with_inventory(product: SourceProduct, stock: dict[str, Any]) -> SourceProduct:
        """按真实变体 ID 汇总 CJ 仓库存；工厂库存不视为可直接上架的现货。"""
        inventories: dict[str, int | None] = {}
        entries = stock.get("variantInventories")
        for item in entries if isinstance(entries, list) else []:
            if not isinstance(item, dict):
                continue
            rows = item.get("inventory")
            values = (
                [row.get("cjInventory") if isinstance(row, dict) else None for row in rows]
                if isinstance(rows, list)
                else []
            )
            valid = bool(values) and all(type(value) is int and value >= 0 for value in values)
            inventories[str(item.get("vid"))] = sum(values) if valid else None
        return product.model_copy(
            update={
                "variants": [
                    variant.model_copy(
                        update={"inventory_available": inventories.get(variant.source_variant_id)}
                    )
                    for variant in product.variants
                ]
            }
        )

    @staticmethod
    def _images(raw: dict[str, Any]) -> list[str]:
        value = raw.get("productImageSet") or []
        if isinstance(value, str) and value.strip().startswith("["):
            try:
                value = json.loads(value)
            except ValueError:
                value = []
        images = value if isinstance(value, list) else str(value).split(",")
        primary = raw.get("bigImage") or raw.get("productImage")
        values = ([primary] if primary else []) + images
        return list(
            dict.fromkeys(
                item.strip()
                for item in values
                if isinstance(item, str) and item.strip().startswith("https://")
            )
        )

    def _variants(
        self,
        raw: dict[str, Any],
        title: str,
    ) -> list[SourceVariant]:
        raw_variants = raw.get("variants") or raw.get("variantList") or []
        if not raw_variants:
            # 列表摘要没有真实变体时保持空列表，不能伪造 vid 和 SKU。
            return []
        return [
            SourceVariant(
                source_variant_id=str(item.get("vid") or item.get("id") or ""),
                sku=str(item.get("variantSku") or item.get("sku") or ""),
                title=str(item.get("variantNameEn") or item.get("variantName") or title),
                price=self.money(item.get("variantSellPrice") or item.get("sellPrice")),
                inventory_available=_first_not_none(
                    item.get("variantInventory"), item.get("inventory")
                ),
            )
            for item in raw_variants
        ]


def _first_not_none(*values: Any) -> Any:
    return next((value for value in values if value is not None), None)
