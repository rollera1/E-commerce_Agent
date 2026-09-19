from ecommerce_agent_ai.agents.copy_seo.deepseek import DeepSeekCopySeoAgent


class OpenAiCompatibleCopySeoAgent(DeepSeekCopySeoAgent):
    """Copy/SEO agent for OpenAI-compatible Chat Completions providers."""

    def __init__(self, api_key: str, base_url: str, model: str, **kwargs: object) -> None:
        super().__init__(
            api_key,
            base_url,
            model,
            include_thinking=False,
            timeout_seconds=300.0,
            trust_env=False,
            **kwargs,
        )
