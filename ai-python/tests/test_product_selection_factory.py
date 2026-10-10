import pytest

from ecommerce_agent_ai.agents.product_selection.agent import ProductSelectionGraphAgent
from ecommerce_agent_ai.agents.product_selection.factory import (
    build_product_selection_agent,
)
from ecommerce_agent_ai.product_sources.fake import FakeProductSource


def test_builds_deterministic_graph_agent_when_llm_is_disabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LLM_ENABLED", "false")

    agent = build_product_selection_agent(FakeProductSource())

    assert isinstance(agent, ProductSelectionGraphAgent)


def test_requires_model_key_when_selection_llm_is_enabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.delenv("LLM_API_KEY", raising=False)

    with pytest.raises(RuntimeError, match="LLM_API_KEY"):
        build_product_selection_agent(FakeProductSource())


def test_rejects_insecure_model_endpoint_for_selection_agent(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LLM_ENABLED", "true")
    monkeypatch.setenv("LLM_API_KEY", "local-secret")
    monkeypatch.setenv("LLM_BASE_URL", "http://api.example.invalid/v1")

    with pytest.raises(RuntimeError, match="HTTPS"):
        build_product_selection_agent(FakeProductSource())
