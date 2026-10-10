import time
from collections.abc import Callable
from enum import StrEnum
from typing import Any

from langchain_core.tools import BaseTool, StructuredTool
from pydantic import Field

from ecommerce_agent_ai.agents.copy_seo.models import (
    CopySeoContent,
    ToolCallStatus,
    ToolCallTrace,
)
from ecommerce_agent_ai.product_sources.models import ApiModel, SourceProduct
from ecommerce_agent_ai.product_sources.port import ProductSource


class InventoryStatus(StrEnum):
    IN_STOCK = "IN_STOCK"
    OUT_OF_STOCK = "OUT_OF_STOCK"
    UNKNOWN = "UNKNOWN"


class ComplianceResult(ApiModel):
    passed: bool
    issues: list[str] = Field(default_factory=list)
    rule_version: str


class GetProductFactsTool:
    """从商品来源读取并裁剪 Agent 所需的可信事实。"""

    name = "get_product_facts"
    description = "根据商品 ID 获取可信的商品、价格、变体和库存事实。"

    def __init__(
        self,
        product_source: ProductSource,
        expected_product_id: str,
        target_market: str,
        *,
        before_call: Callable[[str], None] | None = None,
        traces: list[ToolCallTrace] | None = None,
    ) -> None:
        self._product_source = product_source
        self._expected_product_id = expected_product_id
        self._target_market = target_market
        self._before_call = before_call
        self._traces = traces if traces is not None else []
        self.last_product: SourceProduct | None = None
        self.failure: tuple[str, bool] | None = None

    def execute(self, product_id: str) -> dict[str, Any]:
        started = time.perf_counter()
        if self._before_call is not None:
            self._before_call(self.name)
        if product_id != self._expected_product_id:
            return self._failed(started, "PRODUCT_ID_MISMATCH", found=False)
        product = self._product_source.get_product(product_id)
        if product is None:
            return self._failed(started, "SOURCE_PRODUCT_NOT_FOUND", found=False)
        try:
            facts = product_facts(product, self._target_market)
        except ValueError as error:
            return self._failed(started, str(error), found=True)
        self.last_product = product
        self.failure = None
        self._traces.append(
            ToolCallTrace(
                tool=self.name,
                status=ToolCallStatus.SUCCEEDED,
                duration_ms=_duration_ms(started),
                summary={
                    "sourceProductId": product.source_product_id,
                    "variantCount": len(product.variants),
                    "source": product.source.value,
                },
            )
        )
        return facts

    def as_langchain_tool(self) -> BaseTool:
        """创建可交给 LangGraph Agent 调用的工具适配器。"""
        return StructuredTool.from_function(
            func=self.execute,
            name=self.name,
            description=self.description,
        )

    def _failed(self, started: float, error_type: str, *, found: bool) -> dict[str, Any]:
        self.last_product = None
        self.failure = (error_type, False)
        self._traces.append(
            ToolCallTrace(
                tool=self.name,
                status=ToolCallStatus.FAILED,
                duration_ms=_duration_ms(started),
                error_type=error_type,
            )
        )
        return {"found": found, "error": error_type}


def product_facts(product: SourceProduct, target_market: str) -> dict[str, Any]:
    """返回对模型工具开放的精简事实上下文。"""
    known_inventory = [
        variant.inventory_available
        for variant in product.variants
        if variant.inventory_available is not None
    ]
    unknown_inventory = len(product.variants) - len(known_inventory)
    known_units = sum(known_inventory)
    if known_units > 0:
        inventory_status = InventoryStatus.IN_STOCK
    elif unknown_inventory:
        inventory_status = InventoryStatus.UNKNOWN
    else:
        inventory_status = InventoryStatus.OUT_OF_STOCK

    prices = [product.price, *(variant.price for variant in product.variants)]
    currencies = {price.currency.upper() for price in prices}
    if len(currencies) != 1:
        raise ValueError("PRICE_CURRENCY_MISMATCH")
    amounts = [price.amount for price in prices]
    return {
        "sourceProductId": product.source_product_id,
        "source": product.source.value,
        "title": product.title,
        "description": product.description,
        "targetMarket": target_market,
        "price": {
            "currency": currencies.pop(),
            "minimumAmount": str(min(amounts)),
            "maximumAmount": str(max(amounts)),
        },
        "inventory": {
            "status": inventory_status.value,
            "knownUnits": known_units,
            "unknownVariantCount": unknown_inventory,
        },
        "variants": [
            {
                "sku": variant.sku,
                "title": variant.title,
                "price": variant.price.model_dump(mode="json", by_alias=True),
                "inventoryAvailable": variant.inventory_available,
            }
            for variant in product.variants
        ],
        "fetchedAt": product.fetched_at.isoformat(),
    }


def seo_rules(target_market: str, language: str) -> dict[str, Any]:
    """返回适用于目标市场的确定性输出规则和声明规则。"""
    return {
        "targetMarket": target_market,
        "language": language,
        "outputLanguage": {"en": "英文", "zh": "简体中文"}.get(language, language),
        "titleMaxLength": 255,
        "seoTitleMaxLength": 70,
        "metaDescriptionMaxLength": 160,
        "bulletPointCount": {"minimum": 1, "maximum": 8},
        "tagCount": {"minimum": 1, "maximum": 20},
        "requirements": [
            "标题、卖点、描述、SEO 标题、Meta Description 和标签统一使用指定输出语言；"
            "zh 必须使用简体中文。",
            "仅使用 get_product_facts 返回的事实信息。",
            "不要虚构认证信息、材料、尺寸、保修信息或产品性能。",
            "除非库存状态为有货，否则不要声称商品有库存或可以立即发货。",
            "使用自然的搜索关键词；不要不自然地重复堆砌关键词。",
        ],
    }


class RuleBasedContentValidator:
    version = "copy-compliance-v2"
    _unsupported_claims = (
        "lifetime warranty",
        "medical grade",
        "officially certified",
        "100% waterproof",
    )
    _availability_claims = ("in stock", "available now", "ships immediately")

    def validate(self, content: CopySeoContent, product: SourceProduct) -> ComplianceResult:
        generated_text = " ".join(
            [
                content.title,
                *content.bullet_points,
                content.description,
                content.seo_title,
                content.meta_description,
                *content.tags,
            ]
        ).casefold()
        source_text = f"{product.title} {product.description}".casefold()
        issues = [
            f"UNSUPPORTED_CLAIM:{claim}"
            for claim in self._unsupported_claims
            if claim in generated_text and claim not in source_text
        ]
        known_units = sum(variant.inventory_available or 0 for variant in product.variants)
        if known_units == 0:
            issues.extend(
                f"UNSUPPORTED_AVAILABILITY:{claim}"
                for claim in self._availability_claims
                if claim in generated_text
            )
        return ComplianceResult(
            passed=not issues,
            issues=issues,
            rule_version=self.version,
        )


def _duration_ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
