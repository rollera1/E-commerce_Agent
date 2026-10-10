from typing import Protocol

from ecommerce_agent_ai.product_sources.models import SourceProduct, SourceProductPage


# 该协议类似 Java 接口，CJ 和测试商品源都可以实现它。
# Python 的 Protocol 采用结构化类型：类无须显式声明实现关系，
# 只要提供相同方法签名，就会被视为符合该协议。
class ProductSource(Protocol):
    def list_products(
        self, keyword: str | None, page: int, page_size: int
    ) -> SourceProductPage: ...

    def get_product(self, source_product_id: str) -> SourceProduct | None: ...
