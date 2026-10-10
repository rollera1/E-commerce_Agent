import os

from ecommerce_agent_ai.product_sources.cj import CjProductSource
from ecommerce_agent_ai.product_sources.fake import FakeProductSource
from ecommerce_agent_ai.product_sources.port import ProductSource


# 根据环境变量决定使用真实商品源还是测试商品源。
def build_product_source() -> ProductSource:
    if os.getenv("CJ_ENABLED", "false").lower() != "true":
        return FakeProductSource()
    api_key = os.getenv("CJ_API_KEY", "")
    if not api_key.strip():
        raise RuntimeError("CJ_ENABLED=true requires CJ_API_KEY")
    return CjProductSource(api_key)
