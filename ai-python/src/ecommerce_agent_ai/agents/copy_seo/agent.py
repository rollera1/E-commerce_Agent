import json
import time
from typing import Any
from uuid import uuid4

import openai
from langchain.agents import create_agent
from langchain.agents.structured_output import ToolStrategy
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from langchain_core.tools import tool

from ecommerce_agent_ai.agents.copy_seo.errors import CopySeoAgentError
from ecommerce_agent_ai.agents.copy_seo.models import (
    AgentExecution,
    AgentOutcome,
    AgentTrace,
    CopySeoContent,
    CopySeoGeneration,
    GenerationStatus,
    ReviewStatus,
    ToolCallStatus,
    ToolCallTrace,
)
from ecommerce_agent_ai.agents.copy_seo.tools import (
    GetProductFactsTool,
    RuleBasedContentValidator,
    seo_rules,
)
from ecommerce_agent_ai.product_sources.port import ProductSource

AGENT_VERSION = "copy-seo-react-v1"
PROMPT_VERSION = "copy-seo-react-v1"

SYSTEM_PROMPT = """你是文案与 SEO Agent。
你的目标是基于真实商品事实，为指定市场生成可供商家审核的结构化商品文案。

工作规则：
1. 在输出文案前必须调用 get_product_facts；不要根据商品 ID 猜测商品信息。
2. 由你判断是否需要调用 get_seo_rules，以及是否还需要其他已授权工具。
3. 工具结果不足时继续调用工具；禁止编造认证、材料、尺寸、保修、性能和库存承诺。
4. 信息充分后停止调用业务工具，并通过 CopySeoContent 结构化输出最终结果。
5. 如果收到校验问题，只修订相关内容，再次输出完整 CopySeoContent。
"""


class CopySeoReactAgent:
    """带有调用上限和确定性最终校验的模型工具循环。"""

    def __init__(
        self,
        model: BaseChatModel,
        product_source: ProductSource,
        *,
        max_revisions: int = 1,
        max_tool_calls: int = 4,
        validator: RuleBasedContentValidator | None = None,
    ) -> None:
        if max_revisions < 0:
            raise ValueError("max_revisions must be non-negative")
        if max_tool_calls < 1:
            raise ValueError("max_tool_calls must be positive")
        self.model = model
        self._product_source = product_source
        self._max_revisions = max_revisions
        self._max_tool_calls = max_tool_calls
        self._validator = validator or RuleBasedContentValidator()

    def generate(
        self,
        source_product_id: str,
        target_market: str,
        language: str,
    ) -> CopySeoGeneration:
        started = time.perf_counter()
        traces: list[ToolCallTrace] = []
        tool_call_count = 0

        def begin_tool(name: str) -> float:
            nonlocal tool_call_count
            tool_call_count += 1
            if tool_call_count > self._max_tool_calls:
                raise CopySeoAgentError("TOOL_CALL_LIMIT_EXCEEDED", False)
            return time.perf_counter()

        product_facts_tool = GetProductFactsTool(
            self._product_source,
            source_product_id,
            target_market,
            before_call=begin_tool,
            traces=traces,
        )

        @tool("get_seo_rules")
        def get_rules(market: str, output_language: str) -> dict[str, Any]:
            """获取确定性的 SEO 长度、用词和无依据声明规则。"""
            tool_started = begin_tool("get_seo_rules")
            rules = seo_rules(market, output_language)
            traces.append(
                ToolCallTrace(
                    tool="get_seo_rules",
                    status=ToolCallStatus.SUCCEEDED,
                    duration_ms=_duration_ms(tool_started),
                    summary={"targetMarket": market, "language": output_language},
                )
            )
            return rules

        graph = create_agent(
            model=self.model,
            tools=[product_facts_tool.as_langchain_tool(), get_rules],
            system_prompt=SYSTEM_PROMPT,
            response_format=ToolStrategy(CopySeoContent),
            name="copy_seo_agent",
        )
        messages: list[BaseMessage | dict[str, str]] = [
            {
                "role": "user",
                "content": (
                    f"为商品 {source_product_id} 生成面向 {target_market} 市场、"
                    f"语言为 {language}"
                    f"（{ {'en': '英文', 'zh': '简体中文'}.get(language, language) }）"
                    "的商品文案与 SEO 内容。标题、卖点、描述、SEO 标题、"
                    "Meta Description 和标签均使用指定语言，品牌与 SKU 可保留原文。"
                ),
            }
        ]
        validation_issues: list[str] = []
        revision_count = 0

        for revision in range(self._max_revisions + 1):
            try:
                result = graph.invoke(
                    {"messages": messages},
                    {"recursion_limit": self._max_tool_calls * 2 + 8},
                )
            except CopySeoAgentError as error:
                raise self._with_trace(error, traces, messages, revision_count) from error
            except Exception as error:
                mapped = self._map_model_error(error)
                raise self._with_trace(mapped, traces, messages, revision_count) from error

            messages = result["messages"]
            content = result.get("structured_response")
            if not isinstance(content, CopySeoContent):
                error = CopySeoAgentError("INVALID_OUTPUT", False)
                raise self._with_trace(error, traces, messages, revision_count)
            if product_facts_tool.failure is not None:
                error_type, retryable = product_facts_tool.failure
                error = CopySeoAgentError(error_type, retryable)
                raise self._with_trace(error, traces, messages, revision_count)
            if product_facts_tool.last_product is None:
                validation_issues = ["PRODUCT_FACTS_NOT_COLLECTED"]
            else:
                check_started = time.perf_counter()
                compliance = self._validator.validate(content, product_facts_tool.last_product)
                validation_issues = compliance.issues
                traces.append(
                    ToolCallTrace(
                        tool="validate_copy_seo",
                        status=(
                            ToolCallStatus.SUCCEEDED if compliance.passed else ToolCallStatus.FAILED
                        ),
                        duration_ms=_duration_ms(check_started),
                        summary={
                            "passed": compliance.passed,
                            "issueCount": len(compliance.issues),
                            "ruleVersion": compliance.rule_version,
                        },
                        error_type=None if compliance.passed else "CONTENT_NONCOMPLIANT",
                    )
                )
                if compliance.passed:
                    return self._generation(
                        source_product_id,
                        content,
                        started,
                        traces,
                        messages,
                        revision_count,
                    )

            if revision < self._max_revisions:
                revision_count += 1
                messages = [
                    *messages,
                    HumanMessage(
                        content=(
                            "上一版内容未通过最终校验。请依据以下问题修订，并重新输出完整"
                            f" CopySeoContent：{json.dumps(validation_issues, ensure_ascii=False)}"
                        )
                    ),
                ]

        trace = self._trace(
            AgentOutcome.NEEDS_HUMAN_INPUT,
            traces,
            messages,
            revision_count,
            validation_issues,
            "HUMAN_CONTENT_REVIEW",
        )
        raise CopySeoAgentError("CONTENT_REQUIRES_HUMAN_REVIEW", False, trace=trace)

    def _generation(
        self,
        source_product_id: str,
        content: CopySeoContent,
        started: float,
        traces: list[ToolCallTrace],
        messages: list[BaseMessage],
        revision_count: int,
    ) -> CopySeoGeneration:
        return CopySeoGeneration(
            generation_id=str(uuid4()),
            source_product_id=source_product_id,
            status=GenerationStatus.SUCCEEDED,
            review_status=ReviewStatus.PENDING_REVIEW,
            content=content,
            execution=AgentExecution(
                agent="COPY_SEO",
                agent_version=AGENT_VERSION,
                prompt_version=PROMPT_VERSION,
                model=_model_name(self.model),
                duration_ms=_duration_ms(started),
            ),
            trace=self._trace(
                AgentOutcome.COMPLETED,
                traces,
                messages,
                revision_count,
                [],
                "MERCHANT_REVIEW",
            ),
        )

    @staticmethod
    def _trace(
        outcome: AgentOutcome,
        traces: list[ToolCallTrace],
        messages: list[BaseMessage],
        revision_count: int,
        validation_issues: list[str],
        next_action: str,
    ) -> AgentTrace:
        model_attempts = sum(isinstance(message, AIMessage) for message in messages)
        return AgentTrace(
            current_step=(
                "AWAIT_MERCHANT_REVIEW" if outcome == AgentOutcome.COMPLETED else "AGENT_REVIEW"
            ),
            outcome=outcome,
            steps=["MODEL_DECIDE", "TOOL_LOOP", "STRUCTURED_OUTPUT", "VALIDATE"],
            tool_calls=list(traces),
            model_attempts=model_attempts,
            revision_count=revision_count,
            validation_issues=validation_issues,
            next_action=next_action,
        )

    @classmethod
    def _with_trace(
        cls,
        error: CopySeoAgentError,
        traces: list[ToolCallTrace],
        messages: list[BaseMessage | dict[str, str]],
        revision_count: int,
    ) -> CopySeoAgentError:
        typed_messages = [message for message in messages if isinstance(message, BaseMessage)]
        trace = cls._trace(
            AgentOutcome.NEEDS_HUMAN_INPUT,
            traces,
            typed_messages,
            revision_count,
            [],
            "HUMAN_INPUT_REQUIRED",
        )
        return CopySeoAgentError(error.error_type, error.retryable, trace=trace)

    @staticmethod
    def _map_model_error(error: Exception) -> CopySeoAgentError:
        if isinstance(error, openai.AuthenticationError):
            return CopySeoAgentError("INVALID_CREDENTIALS", False)
        if isinstance(error, openai.PermissionDeniedError):
            return CopySeoAgentError("PERMISSION_DENIED", False)
        if isinstance(error, openai.RateLimitError):
            return CopySeoAgentError("RATE_LIMITED", True)
        if isinstance(error, openai.APITimeoutError):
            return CopySeoAgentError("TIMEOUT", True)
        if isinstance(error, openai.APIConnectionError):
            return CopySeoAgentError("NETWORK_ERROR", True)
        if isinstance(error, openai.InternalServerError):
            return CopySeoAgentError("UPSTREAM_UNAVAILABLE", True)
        return CopySeoAgentError("MODEL_EXECUTION_FAILED", False)


def _duration_ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)


def _model_name(model: BaseChatModel) -> str:
    return str(
        getattr(model, "configured_model", None)
        or getattr(model, "model_name", None)
        or getattr(model, "model", None)
        or model._llm_type
    )
