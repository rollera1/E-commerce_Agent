import pytest

from ecommerce_agent_ai.agents.copy_seo.agent import CopySeoReactAgent
from ecommerce_agent_ai.agents.copy_seo.factory import build_copy_seo_agent
from ecommerce_agent_ai.agents.copy_seo.fake import FakeCopySeoAgent
from ecommerce_agent_ai.agents.llm import OpenAiCompatibleAgentModel
from ecommerce_agent_ai.product_sources.fake import FakeProductSource


def test_uses_fake_when_llm_is_disabled(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_ENABLED", "false")

    agent = build_copy_seo_agent(FakeProductSource())

    assert isinstance(agent, FakeCopySeoAgent)


def test_requires_generic_model_key_when_enabled(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.delenv("LLM_API_KEY", raising=False)

    with pytest.raises(RuntimeError, match="LLM_API_KEY"):
        build_copy_seo_agent(FakeProductSource())


def test_builds_apinebula_react_agent_by_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.setenv("LLM_API_KEY", "local-secret")
    monkeypatch.delenv("LLM_BASE_URL", raising=False)
    monkeypatch.delenv("LLM_MODEL", raising=False)

    agent = build_copy_seo_agent(FakeProductSource())

    assert isinstance(agent, CopySeoReactAgent)
    assert isinstance(agent.model, OpenAiCompatibleAgentModel)
    assert agent.model.openai_api_base == "https://apinebula.ai/v1"
    assert agent.model.model_name == "gpt-6-sol"


def test_rejects_non_https_model_endpoint(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.setenv("LLM_API_KEY", "local-secret")
    monkeypatch.setenv("LLM_BASE_URL", "http://api.example.invalid/v1")

    with pytest.raises(RuntimeError, match="HTTPS"):
        build_copy_seo_agent(FakeProductSource())


@pytest.mark.parametrize(
    ("name", "value"),
    [
        ("COPY_SEO_MAX_REVISIONS", "invalid"),
        ("COPY_SEO_MAX_REVISIONS", "-1"),
        ("COPY_SEO_MAX_REVISIONS", "4"),
        ("COPY_SEO_MAX_TOOL_CALLS", "0"),
        ("COPY_SEO_MAX_TOOL_CALLS", "11"),
    ],
)
def test_rejects_invalid_agent_limits(
    monkeypatch: pytest.MonkeyPatch,
    name: str,
    value: str,
) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.setenv("LLM_API_KEY", "local-secret")
    monkeypatch.setenv(name, value)

    with pytest.raises(RuntimeError, match=name):
        build_copy_seo_agent(FakeProductSource())
