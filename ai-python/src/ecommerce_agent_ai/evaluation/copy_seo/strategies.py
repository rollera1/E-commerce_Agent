import json
import time
from uuid import uuid4

from langchain_core.language_models import BaseChatModel

from ecommerce_agent_ai.agents.copy_seo.agent import CopySeoReactAgent
from ecommerce_agent_ai.agents.copy_seo.fake import FakeCopySeoAgent
from ecommerce_agent_ai.agents.copy_seo.models import (
    AgentExecution,
    CopySeoContent,
    CopySeoGeneration,
    GenerationStatus,
    ReviewStatus,
)
from ecommerce_agent_ai.product_sources.models import SourceProduct, SourceProductPage


class StaticProductSource:
    def __init__(self, product: SourceProduct) -> None:
        self._product = product

    def get_product(self, source_product_id: str) -> SourceProduct | None:
        return self._product if self._product.source_product_id == source_product_id else None

    def list_products(self, keyword: str | None, page: int, page_size: int) -> SourceProductPage:
        del keyword
        items = [self._product] if page == 1 and page_size > 0 else []
        return SourceProductPage(items=items, page=page, page_size=page_size, total=1)


class TemplateBaseline:
    """使用确定性商品字段插值的非大模型对照组。"""

    def generate(
        self, product: SourceProduct, target_market: str, language: str
    ) -> CopySeoGeneration:
        del target_market, language
        title = product.title.strip()
        description = product.description.strip()
        words = [word.strip(".,-/").casefold() for word in title.split() if len(word) > 2]
        tags = list(dict.fromkeys(words))[:5] or ["product"]
        return _generation(
            product,
            CopySeoContent(
                title=title[:255],
                bullet_points=[
                    description[:180],
                    f"Price: {product.price.amount} {product.price.currency}",
                ],
                description=description,
                seo_title=title[:70],
                meta_description=description[:160],
                tags=tags,
            ),
            "TEMPLATE_BASELINE",
            "template-v1",
            "not-applicable",
            "none",
            0,
        )


class AgentStrategy:
    def __init__(self, agent: object) -> None:
        self._agent = agent

    def generate(
        self, product: SourceProduct, target_market: str, language: str
    ) -> CopySeoGeneration:
        return self._agent.generate(  # type: ignore[attr-defined]
            product.source_product_id, target_market, language
        )


class SinglePassModelStrategy:
    def __init__(self, model: BaseChatModel) -> None:
        self._model = model.with_structured_output(CopySeoContent, method="function_calling")
        self._model_name = str(
            getattr(model, "configured_model", None) or getattr(model, "model_name", "unknown")
        )

    def generate(
        self, product: SourceProduct, target_market: str, language: str
    ) -> CopySeoGeneration:
        started = time.perf_counter()
        content = self._model.invoke(
            "生成准确的电商文案和 SEO 内容，并使用结构化输出。"
            "不要编造事实。 Input: "
            + json.dumps(
                {
                    "targetMarket": target_market,
                    "language": language,
                    "product": product.model_dump(mode="json", by_alias=True),
                },
                ensure_ascii=False,
            )
        )
        if not isinstance(content, CopySeoContent):
            content = CopySeoContent.model_validate(content)
        return _generation(
            product,
            content,
            "SINGLE_PASS",
            "single-pass-v2",
            "single-pass-v2",
            self._model_name,
            int((time.perf_counter() - started) * 1000),
        )


# 3 种 Copy/SEO 生成策略template_baseline、single_pass、tool_agent
# 同一个商品 SourceProduct
#        │
#        ├── ① template_baseline
#        │       模板规则生成，不使用 LLM
#        │
#        ├── ② single_pass
#        │       LLM 一次生成，不使用 Agent 工具链
#        │
#        └── ③ tool_agent
#                ReAct Agent + Tool + 校验/修订


def build_strategies(
    product: SourceProduct,
    model: BaseChatModel | None = None,
    max_revisions: int = 1,
) -> dict[str, object]:
    source = StaticProductSource(product)
    if model is None:
        single_pass: object = AgentStrategy(FakeCopySeoAgent(source))
        tool_agent: object = AgentStrategy(FakeCopySeoAgent(source))
    else:
        single_pass = SinglePassModelStrategy(model)
        tool_agent = AgentStrategy(CopySeoReactAgent(model, source, max_revisions=max_revisions))
    return {
        "template_baseline": TemplateBaseline(),
        "single_pass": single_pass,
        "tool_agent": tool_agent,
    }


def _generation(
    product: SourceProduct,
    content: CopySeoContent,
    agent: str,
    agent_version: str,
    prompt_version: str,
    model: str,
    duration_ms: int,
) -> CopySeoGeneration:
    return CopySeoGeneration(
        generation_id=str(uuid4()),
        source_product_id=product.source_product_id,
        status=GenerationStatus.SUCCEEDED,
        review_status=ReviewStatus.PENDING_REVIEW,
        content=content,
        execution=AgentExecution(
            agent=agent,
            agent_version=agent_version,
            prompt_version=prompt_version,
            model=model,
            duration_ms=duration_ms,
        ),
    )
