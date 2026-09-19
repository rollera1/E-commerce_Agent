import pytest

from ecommerce_agent_ai.agents.copy_seo.deepseek import DeepSeekCopySeoAgent
from ecommerce_agent_ai.agents.copy_seo.factory import build_copy_seo_agent
from ecommerce_agent_ai.agents.copy_seo.fake import FakeCopySeoAgent
from ecommerce_agent_ai.agents.copy_seo.openai_compatible import (
    OpenAiCompatibleCopySeoAgent,
)


def test_uses_fake_when_llm_is_disabled(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_ENABLED", "false")
    assert isinstance(build_copy_seo_agent(), FakeCopySeoAgent)


def test_requires_deepseek_key_when_enabled(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    with pytest.raises(RuntimeError, match="LLM_API_KEY"):
        build_copy_seo_agent()


def test_builds_deepseek_agent_with_https_endpoint(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "local-secret")
    monkeypatch.setenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-v4-flash")
    assert isinstance(build_copy_seo_agent(), DeepSeekCopySeoAgent)


def test_rejects_non_https_model_endpoint(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "local-secret")
    monkeypatch.setenv("DEEPSEEK_BASE_URL", "http://api.deepseek.com")
    with pytest.raises(RuntimeError, match="HTTPS"):
        build_copy_seo_agent()


def test_builds_apinebula_agent_with_documented_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.setenv("LLM_PROVIDER", "apinebula")
    monkeypatch.setenv("LLM_API_KEY", "local-secret")
    monkeypatch.delenv("LLM_BASE_URL", raising=False)
    monkeypatch.delenv("LLM_MODEL", raising=False)

    agent = build_copy_seo_agent()

    assert isinstance(agent, OpenAiCompatibleCopySeoAgent)
    assert agent._base_url == "https://api.yhlxj.ai/v1"
    assert agent._model == "gpt-5.6-terra"
    assert agent._client.timeout.read == 300.0


def test_rejects_unknown_llm_provider(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.setenv("LLM_PROVIDER", "unknown")

    with pytest.raises(RuntimeError, match="Unsupported LLM_PROVIDER"):
        build_copy_seo_agent()
