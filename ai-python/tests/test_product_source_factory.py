import pytest

from ecommerce_agent_ai.product_sources.cj import CjProductSource
from ecommerce_agent_ai.product_sources.factory import build_product_source
from ecommerce_agent_ai.product_sources.fake import FakeProductSource


def test_uses_fake_by_default(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("CJ_ENABLED", raising=False)
    assert isinstance(build_product_source(), FakeProductSource)


def test_requires_key_when_cj_is_enabled(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("CJ_ENABLED", "true")
    monkeypatch.delenv("CJ_API_KEY", raising=False)
    with pytest.raises(RuntimeError, match="CJ_API_KEY"):
        build_product_source()


def test_builds_cj_source_only_when_explicitly_enabled(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("CJ_ENABLED", "true")
    monkeypatch.setenv("CJ_API_KEY", "local-secret")
    assert isinstance(build_product_source(), CjProductSource)
