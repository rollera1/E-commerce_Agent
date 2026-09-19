import json

import httpx
import pytest

from ecommerce_agent_ai.agents.copy_seo.deepseek import (
    DeepSeekCopySeoAgent,
    LlmAgentError,
)
from ecommerce_agent_ai.product_sources.fake import FakeProductSource


def product():
    result = FakeProductSource().get_product("fake-desk-lamp-001")
    assert result is not None
    return result


def valid_content() -> dict:
    return {
        "title": "Focused Workspace LED Desk Lamp",
        "bulletPoints": ["Adjustable light", "Compact design"],
        "description": "An adjustable LED desk lamp for focused work.",
        "seoTitle": "LED Desk Lamp for Focused Work",
        "metaDescription": "Shop an adjustable LED desk lamp for focused home-office work.",
        "tags": ["desk lamp", "LED"],
    }


def test_generates_validated_content_without_exposing_key() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            200, json={"choices": [{"message": {"content": json.dumps(valid_content())}}]}
        )

    agent = DeepSeekCopySeoAgent(
        "secret-key",
        "https://api.deepseek.com",
        "deepseek-v4-flash",
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        sleeper=lambda _: None,
    )
    result = agent.generate(product(), "US", "en")

    body = json.loads(captured[0].content)
    assert captured[0].headers["Authorization"] == "Bearer secret-key"
    assert body["response_format"] == {"type": "json_object"}
    assert body["thinking"] == {"type": "disabled"}
    assert result.content.title == "Focused Workspace LED Desk Lamp"
    assert result.execution.model == "deepseek-v4-flash"
    assert "secret-key" not in result.model_dump_json()


def test_retries_rate_limit_once_then_succeeds() -> None:
    attempts = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            return httpx.Response(429, json={"error": {"message": "rate limited"}})
        return httpx.Response(
            200, json={"choices": [{"message": {"content": json.dumps(valid_content())}}]}
        )

    agent = DeepSeekCopySeoAgent(
        "secret-key",
        "https://api.deepseek.com",
        "deepseek-v4-flash",
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        sleeper=lambda _: None,
    )
    assert agent.generate(product(), "US", "en").status == "SUCCEEDED"
    assert attempts == 2


@pytest.mark.parametrize(
    "status,error_type", [(401, "INVALID_CREDENTIALS"), (403, "PERMISSION_DENIED")]
)
def test_does_not_retry_authentication_failures(status: int, error_type: str) -> None:
    attempts = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(status, json={"error": {"message": "denied"}})

    agent = DeepSeekCopySeoAgent(
        "secret-key",
        "https://api.deepseek.com",
        "deepseek-v4-flash",
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        sleeper=lambda _: None,
    )
    with pytest.raises(LlmAgentError) as raised:
        agent.generate(product(), "US", "en")
    assert raised.value.error_type == error_type
    assert raised.value.retryable is False
    assert attempts == 1


def test_rejects_structurally_invalid_json_output() -> None:
    attempts = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"title":"only"}'}}]})

    agent = DeepSeekCopySeoAgent(
        "secret-key",
        "https://api.deepseek.com",
        "deepseek-v4-flash",
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        sleeper=lambda _: None,
    )
    with pytest.raises(LlmAgentError) as raised:
        agent.generate(product(), "US", "en")
    assert raised.value.error_type == "INVALID_OUTPUT"
    assert attempts == 2


def test_retries_timeout_only_once() -> None:
    attempts = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        raise httpx.ReadTimeout("timed out", request=request)

    agent = DeepSeekCopySeoAgent(
        "secret-key",
        "https://api.deepseek.com",
        "deepseek-v4-flash",
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        sleeper=lambda _: None,
    )
    with pytest.raises(LlmAgentError) as raised:
        agent.generate(product(), "US", "en")
    assert raised.value.error_type == "TIMEOUT"
    assert raised.value.retryable is True
    assert attempts == 2
