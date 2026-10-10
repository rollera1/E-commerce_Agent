import os
from urllib.parse import urlparse

from langchain_openai import ChatOpenAI
from openai import DefaultAsyncHttpxClient, DefaultHttpxClient

DEFAULT_APINEBULA_BASE_URL = "https://apinebula.ai/v1"
DEFAULT_APINEBULA_MODEL = "gpt-6-sol"


class OpenAiCompatibleAgentModel(ChatOpenAI):
    """供不同业务 Agent 共用的 OpenAI 兼容模型适配器。"""

    def __init__(
        self,
        api_key: str,
        base_url: str,
        model: str,
        *,
        timeout_seconds: float = 300.0,
    ) -> None:
        if not api_key.strip():
            raise ValueError("LLM API key must not be blank")
        super().__init__(
            api_key=api_key,
            base_url=base_url.rstrip("/"),
            model=model,
            temperature=0.2,
            timeout=timeout_seconds,
            max_retries=1,
            use_responses_api=False,
            http_client=DefaultHttpxClient(trust_env=False),
            http_async_client=DefaultAsyncHttpxClient(trust_env=False),
        )


def build_agent_model() -> OpenAiCompatibleAgentModel:
    """根据统一环境变量创建供业务 Agent 使用的模型。"""
    api_key = os.getenv("LLM_API_KEY", "")
    base_url = os.getenv("LLM_BASE_URL", DEFAULT_APINEBULA_BASE_URL)
    model = os.getenv("LLM_MODEL", DEFAULT_APINEBULA_MODEL)
    if not api_key.strip():
        raise RuntimeError("LLM_ENABLED=true requires LLM_API_KEY")
    if urlparse(base_url).scheme != "https":
        raise RuntimeError("LLM_BASE_URL must use HTTPS")
    return OpenAiCompatibleAgentModel(api_key, base_url, model)
