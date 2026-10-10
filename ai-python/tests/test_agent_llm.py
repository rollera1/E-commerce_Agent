import pytest

from ecommerce_agent_ai.agents.llm import OpenAiCompatibleAgentModel


def test_configures_apinebula_as_an_openai_compatible_model() -> None:
    model = OpenAiCompatibleAgentModel(
        "secret-key",
        "https://apinebula.ai/v1/",
        "gpt-6-sol",
    )

    assert model.openai_api_base == "https://apinebula.ai/v1"
    assert model.model_name == "gpt-6-sol"
    assert model.http_client is not None
    assert model.http_client._trust_env is False
    assert "secret-key" not in repr(model)


def test_rejects_a_blank_model_key() -> None:
    with pytest.raises(ValueError, match="must not be blank"):
        OpenAiCompatibleAgentModel(" ", "https://api.example.invalid/v1", "model")
