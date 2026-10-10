import json
from datetime import UTC, datetime
from pathlib import Path

from ecommerce_agent_ai.agents.copy_seo.agent import CopySeoReactAgent
from ecommerce_agent_ai.agents.llm import build_agent_model
from ecommerce_agent_ai.agents.product_selection.agent import ProductSelectionGraphAgent
from ecommerce_agent_ai.agents.product_selection.reasoner import LlmProductSelectionReasoner
from ecommerce_agent_ai.product_sources.factory import build_product_source
from ecommerce_agent_ai.product_sources.models import SourceProductPage
from ecommerce_agent_ai.product_sources.port import ProductSource


class FirstSearchEmptyProductSource:
    """首次返回空页，用真实第二次查询验收关键词改写分支。"""

    def __init__(self, source: ProductSource) -> None:
        self._source = source
        self._search_count = 0

    def list_products(
        self,
        keyword: str | None,
        page: int,
        page_size: int,
    ) -> SourceProductPage:
        self._search_count += 1
        if self._search_count == 1:
            return SourceProductPage(items=[], page=page, page_size=page_size, total=0)
        return self._source.list_products(keyword, page, page_size)

    def get_product(self, source_product_id: str):
        return self._source.get_product(source_product_id)


def main() -> None:
    output = Path("evaluation/product_selection/results/online-acceptance.json")
    source = build_product_source()
    model = build_agent_model()

    print("[1/3] 执行中文商品意图的真实 CJ 检索", flush=True)
    selection_agent = ProductSelectionGraphAgent(LlmProductSelectionReasoner(model), source)
    direct = selection_agent.select("宠物牵引绳", 5)
    if not direct.candidates:
        raise RuntimeError("真实 CJ 中文意图检索没有返回候选商品")

    print("[2/3] 执行一次关键词改写和真实 CJ 回退检索", flush=True)
    rewrite_agent = ProductSelectionGraphAgent(
        LlmProductSelectionReasoner(model),
        FirstSearchEmptyProductSource(source),
    )
    rewritten = rewrite_agent.select("女士夏季休闲短裤", 5)
    if rewritten.trace.rewrite_count != 1 or not rewritten.candidates:
        raise RuntimeError("真实模型未完成一次关键词改写并取得真实 CJ 候选商品")

    print("[3/3] 执行真实文案 Tool Calling", flush=True)
    copy_result = CopySeoReactAgent(model, source).generate(
        direct.candidates[0].source_product_id,
        "US",
        "en",
    )
    if copy_result.trace is None or not any(
        call.tool == "get_product_facts" for call in copy_result.trace.tool_calls
    ):
        raise RuntimeError("文案 Agent 未调用商品事实工具")

    report = {
        "executedAt": datetime.now(UTC).isoformat(),
        "selection": {
            "input": direct.query,
            "matchedQuery": direct.matched_query,
            "candidateCount": len(direct.candidates),
            "searchCount": direct.trace.search_count,
            "modelAttempts": direct.trace.model_attempts,
        },
        "rewrite": {
            "input": rewritten.query,
            "matchedQuery": rewritten.matched_query,
            "candidateCount": len(rewritten.candidates),
            "searchCount": rewritten.trace.search_count,
            "rewriteCount": rewritten.trace.rewrite_count,
            "modelAttempts": rewritten.trace.model_attempts,
        },
        "copySeo": {
            "sourceProductId": copy_result.source_product_id,
            "status": copy_result.status,
            "reviewStatus": copy_result.review_status,
            "agentVersion": copy_result.execution.agent_version,
            "promptVersion": copy_result.execution.prompt_version,
            "model": copy_result.execution.model,
            "modelAttempts": copy_result.trace.model_attempts,
            "toolCalls": [call.tool for call in copy_result.trace.tool_calls],
            "nextAction": copy_result.trace.next_action,
        },
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
