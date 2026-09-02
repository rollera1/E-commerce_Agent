from typing import Protocol

from ecommerce_agent_ai.product_sources.models import SourceProduct, SourceProductPage


class ProductSource(Protocol):
    def list_products(
        self, keyword: str | None, page: int, page_size: int
    ) -> SourceProductPage: ...

    def get_product(self, source_product_id: str) -> SourceProduct | None: ...
