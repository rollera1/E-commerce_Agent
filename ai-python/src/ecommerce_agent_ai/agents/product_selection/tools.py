from ecommerce_agent_ai.product_sources.models import SourceProductPage
from ecommerce_agent_ai.product_sources.port import ProductSource


class SearchSourceProductsTool:
    """通过统一商品来源接口检索候选商品。"""

    name = "search_source_products"

    def __init__(self, product_source: ProductSource) -> None:
        self._product_source = product_source

    def execute(self, keyword: str, limit: int) -> SourceProductPage:
        normalized_keyword = keyword.strip()
        if not normalized_keyword:
            raise ValueError("keyword must not be blank")
        if limit < 1 or limit > 100:
            raise ValueError("limit must be between 1 and 100")
        return self._product_source.list_products(normalized_keyword, 1, limit)
