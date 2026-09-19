import json

import httpx

from ecommerce_agent_ai.agents.copy_seo.openai_compatible import (
    OpenAiCompatibleCopySeoAgent,
)
from ecommerce_agent_ai.product_sources.fake import FakeProductSource


def test_uses_openai_compatible_request_without_deepseek_thinking_field() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        content = {
            "title": "Desk Lamp",
            "bulletPoints": ["Adjustable light"],
            "description": "A compact adjustable desk lamp.",
            "seoTitle": "Adjustable Desk Lamp",
            "metaDescription": "A compact adjustable desk lamp for focused work.",
            "tags": ["desk lamp"],
        }
        return httpx.Response(
            200, json={"choices": [{"message": {"content": json.dumps(content)}}]}
        )

    product = FakeProductSource().get_product("fake-desk-lamp-001")
    assert product is not None
    agent = OpenAiCompatibleCopySeoAgent(
        "secret-key",
        "https://api.yhlxj.ai/v1",
        "gpt-5.6-terra",
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        sleeper=lambda _: None,
    )

    result = agent.generate(product, "US", "en")

    body = json.loads(captured[0].content)
    assert captured[0].url == "https://api.yhlxj.ai/v1/chat/completions"
    assert "thinking" not in body
    assert body["model"] == "gpt-5.6-terra"
    assert result.execution.model == "gpt-5.6-terra"
