import json
import time
from collections.abc import Callable
from typing import Any
from uuid import uuid4

import httpx
from pydantic import ValidationError

from ecommerce_agent_ai.agents.copy_seo.models import (
    AgentExecution,
    CopySeoContent,
    CopySeoGeneration,
    GenerationStatus,
    ReviewStatus,
)
from ecommerce_agent_ai.product_sources.models import SourceProduct

AGENT_VERSION = "deepseek-v1"
PROMPT_VERSION = "copy-seo-v2"


class LlmAgentError(RuntimeError):
    def __init__(self, error_type: str, retryable: bool) -> None:
        super().__init__(f"Copy/SEO model call failed: {error_type}")
        self.error_type = error_type
        self.retryable = retryable


class DeepSeekCopySeoAgent:
    def __init__(
        self,
        api_key: str,
        base_url: str,
        model: str,
        *,
        client: httpx.Client | None = None,
        sleeper: Callable[[float], None] = time.sleep,
        include_thinking: bool = True,
        timeout_seconds: float = 30.0,
        trust_env: bool = True,
    ) -> None:
        if not api_key.strip():
            raise ValueError("DeepSeek API key must not be blank")
        self._api_key = api_key
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._client = client or httpx.Client(
            timeout=timeout_seconds,
            trust_env=trust_env,
        )
        self._sleeper = sleeper
        self._include_thinking = include_thinking

    def generate(
        self, product: SourceProduct, target_market: str, language: str
    ) -> CopySeoGeneration:
        started = time.perf_counter()
        body = self._request_body(product, target_market, language)
        content: CopySeoContent | None = None
        last_error: LlmAgentError | None = None
        for output_attempt in range(2):
            response = self._call_with_retry(body)
            try:
                content = self._validated_content(response)
                break
            except LlmAgentError as error:
                last_error = error
                if output_attempt == 0:
                    body["messages"].append(
                        {
                            "role": "user",
                            "content": (
                                "The previous response failed schema validation. Return JSON only "
                                "with every required field and obey all length and array limits."
                            ),
                        }
                    )
        if content is None:
            raise last_error or LlmAgentError("INVALID_OUTPUT", False)
        duration_ms = int((time.perf_counter() - started) * 1000)
        return CopySeoGeneration(
            generation_id=str(uuid4()),
            source_product_id=product.source_product_id,
            status=GenerationStatus.SUCCEEDED,
            review_status=ReviewStatus.PENDING_REVIEW,
            content=content,
            execution=AgentExecution(
                agent="COPY_SEO",
                agent_version=AGENT_VERSION,
                prompt_version=PROMPT_VERSION,
                model=self._model,
                duration_ms=duration_ms,
            ),
        )

    def _call_with_retry(self, body: dict[str, Any]) -> httpx.Response:
        last_error: LlmAgentError | None = None
        for attempt in range(2):
            try:
                response = self._client.post(
                    f"{self._base_url}/chat/completions",
                    headers={"Authorization": f"Bearer {self._api_key}"},
                    json=body,
                )
            except httpx.TimeoutException as error:
                last_error = LlmAgentError("TIMEOUT", True)
                if attempt == 0:
                    self._sleeper(0.25)
                    continue
                raise last_error from error
            except httpx.RequestError as error:
                last_error = LlmAgentError("NETWORK_ERROR", True)
                if attempt == 0:
                    self._sleeper(0.25)
                    continue
                raise last_error from error

            if response.status_code == 401:
                raise LlmAgentError("INVALID_CREDENTIALS", False)
            if response.status_code == 403:
                raise LlmAgentError("PERMISSION_DENIED", False)
            if response.status_code == 429 or response.status_code >= 500:
                last_error = LlmAgentError(
                    "RATE_LIMITED" if response.status_code == 429 else "UPSTREAM_UNAVAILABLE",
                    True,
                )
                if attempt == 0:
                    self._sleeper(0.25)
                    continue
                raise last_error
            if response.is_error:
                raise LlmAgentError("UPSTREAM_REJECTED", False)
            return response
        raise last_error or LlmAgentError("UNKNOWN", False)

    @staticmethod
    def _validated_content(response: httpx.Response) -> CopySeoContent:
        try:
            payload = response.json()
            raw_content = payload["choices"][0]["message"]["content"]
            if not isinstance(raw_content, str) or not raw_content.strip():
                raise ValueError("empty content")
            return CopySeoContent.model_validate_json(raw_content)
        except (ValueError, KeyError, IndexError, TypeError, ValidationError) as error:
            raise LlmAgentError("INVALID_OUTPUT", False) from error

    def _request_body(
        self, product: SourceProduct, target_market: str, language: str
    ) -> dict[str, Any]:
        input_product = {
            "title": product.title,
            "description": product.description,
            "price": product.price.model_dump(mode="json", by_alias=True),
            "variants": [
                variant.model_dump(mode="json", by_alias=True) for variant in product.variants
            ],
        }
        system_prompt = (
            "You are an ecommerce copy and SEO specialist. Return JSON only. "
            "Do not invent certifications, guarantees, materials, dimensions, "
            "or performance claims. "
            "The JSON must contain exactly title, bulletPoints, description, seoTitle, "
            "metaDescription, and tags. title is at most 255 characters; bulletPoints is "
            "an array of 1 to 8 non-empty strings; seoTitle is at most 70 characters; "
            "metaDescription is at most 160 characters; tags is an array of 1 to 20 "
            "non-empty strings."
        )
        user_prompt = json.dumps(
            {
                "task": "Create accurate merchant-review copy and SEO content",
                "targetMarket": target_market,
                "language": language,
                "product": input_product,
                "exampleJsonShape": {
                    "title": "string",
                    "bulletPoints": ["string"],
                    "description": "string",
                    "seoTitle": "string",
                    "metaDescription": "string",
                    "tags": ["string"],
                },
            },
            ensure_ascii=False,
        )
        body: dict[str, Any] = {
            "model": self._model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "response_format": {"type": "json_object"},
            "temperature": 0.2,
            "max_tokens": 1200,
            "stream": False,
        }
        if self._include_thinking:
            body["thinking"] = {"type": "disabled"}
        return body
