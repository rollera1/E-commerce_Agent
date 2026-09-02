from datetime import UTC, datetime
from decimal import Decimal

from ecommerce_agent_ai.product_sources.models import (
    Money,
    ProductSourceName,
    SourceProduct,
    SourceProductPage,
    SourceVariant,
)


class FakeProductSource:
    """Deterministic product source for development and automated tests."""

    def __init__(self) -> None:
        self._products = (_desk_lamp(),)

    def list_products(self, keyword: str | None, page: int, page_size: int) -> SourceProductPage:
        normalized_keyword = (keyword or "").strip().casefold()
        matches = [
            product
            for product in self._products
            if not normalized_keyword
            or normalized_keyword in product.title.casefold()
            or normalized_keyword in product.description.casefold()
        ]
        start = (page - 1) * page_size
        return SourceProductPage(
            items=matches[start : start + page_size],
            page=page,
            page_size=page_size,
            total=len(matches),
        )

    def get_product(self, source_product_id: str) -> SourceProduct | None:
        return next(
            (
                product
                for product in self._products
                if product.source_product_id == source_product_id
            ),
            None,
        )


def _desk_lamp() -> SourceProduct:
    price = Money(amount=Decimal("29.90"), currency="USD")
    return SourceProduct(
        source=ProductSourceName.FAKE,
        source_product_id="fake-desk-lamp-001",
        title="Adjustable LED Desk Lamp",
        description="A compact dimmable desk lamp for home offices.",
        images=["https://example.invalid/products/desk-lamp-001.jpg"],
        price=price,
        variants=[
            SourceVariant(
                source_variant_id="fake-desk-lamp-black",
                sku="FAKE-LAMP-BLK",
                title="Black",
                price=price,
                inventory_available=50,
            )
        ],
        source_url="https://example.invalid/products/fake-desk-lamp-001",
        fetched_at=datetime(2026, 9, 2, tzinfo=UTC),
    )
