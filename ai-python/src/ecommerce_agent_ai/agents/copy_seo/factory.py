import os
from urllib.parse import urlparse

from ecommerce_agent_ai.agents.copy_seo.deepseek import DeepSeekCopySeoAgent
from ecommerce_agent_ai.agents.copy_seo.fake import FakeCopySeoAgent
from ecommerce_agent_ai.agents.copy_seo.openai_compatible import (
    OpenAiCompatibleCopySeoAgent,
)
from ecommerce_agent_ai.agents.copy_seo.port import CopySeoAgent


def build_copy_seo_agent() -> CopySeoAgent:
    if os.getenv("LLM_ENABLED", "false").lower() != "true":
        return FakeCopySeoAgent()
    provider = os.getenv("LLM_PROVIDER", "deepseek").strip().lower()
    if provider not in {"deepseek", "apinebula"}:
        raise RuntimeError(f"Unsupported LLM_PROVIDER: {provider}")

    if provider == "apinebula":
        api_key = os.getenv("LLM_API_KEY", "")
        base_url = os.getenv("LLM_BASE_URL", "https://api.yhlxj.ai/v1")
        model = os.getenv("LLM_MODEL", "gpt-5.6-terra")
    else:
        api_key = os.getenv("LLM_API_KEY") or os.getenv("DEEPSEEK_API_KEY", "")
        base_url = os.getenv("LLM_BASE_URL") or os.getenv(
            "DEEPSEEK_BASE_URL", "https://api.deepseek.com"
        )
        model = os.getenv("LLM_MODEL") or os.getenv(
            "DEEPSEEK_MODEL", "deepseek-v4-flash"
        )
    if not api_key.strip():
        raise RuntimeError("LLM_ENABLED=true requires LLM_API_KEY")
    if urlparse(base_url).scheme != "https":
        raise RuntimeError("LLM_BASE_URL must use HTTPS")
    if provider == "apinebula":
        return OpenAiCompatibleCopySeoAgent(api_key, base_url, model)
    return DeepSeekCopySeoAgent(api_key, base_url, model)
