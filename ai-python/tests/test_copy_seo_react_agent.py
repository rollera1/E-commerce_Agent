from collections.abc import Callable, Sequence
from typing import Any

import pytest
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_core.runnables import Runnable
from langchain_core.tools import BaseTool

from ecommerce_agent_ai.agents.copy_seo.agent import CopySeoReactAgent
from ecommerce_agent_ai.agents.copy_seo.errors import CopySeoAgentError
from ecommerce_agent_ai.product_sources.fake import FakeProductSource


class ScriptedToolCallingModel(BaseChatModel):
    responses: list[AIMessage]
    index: int = 0
    bound_tool_names: list[str] = []

    @property
    def _llm_type(self) -> str:
        return "scripted-tool-calling-model"

    def _generate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: Any = None,
        **kwargs: Any,
    ) -> ChatResult:
        del messages, stop, run_manager, kwargs
        response = self.responses[self.index]
        self.index += 1
        return ChatResult(generations=[ChatGeneration(message=response)])

    def bind_tools(
        self,
        tools: Sequence[dict[str, Any] | type | Callable[..., Any] | BaseTool],
        *,
        tool_choice: str | None = None,
        **kwargs: Any,
    ) -> Runnable[Any, AIMessage]:
        del tool_choice, kwargs
        self.bound_tool_names = [
            tool.name if isinstance(tool, BaseTool) else getattr(tool, "__name__", "schema")
            for tool in tools
        ]
        return self


def tool_call(name: str, arguments: dict[str, Any], call_id: str) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[{"name": name, "args": arguments, "id": call_id}],
    )


def copy_output(*, description: str = "A compact adjustable desk lamp.") -> AIMessage:
    return tool_call(
        "CopySeoContent",
        {
            "title": "Adjustable LED Desk Lamp",
            "bulletPoints": ["Adjustable light for focused desk work"],
            "description": description,
            "seoTitle": "Adjustable LED Desk Lamp",
            "metaDescription": "A compact adjustable LED desk lamp for focused work.",
            "tags": ["desk lamp", "LED lighting"],
        },
        "structured-output",
    )


def test_model_decides_to_call_tools_then_returns_structured_copy() -> None:
    model = ScriptedToolCallingModel(
        responses=[
            tool_call(
                "get_product_facts",
                {"product_id": "fake-desk-lamp-001"},
                "product-call",
            ),
            tool_call(
                "get_seo_rules",
                {"market": "US", "output_language": "en"},
                "rules-call",
            ),
            copy_output(),
        ]
    )

    result = CopySeoReactAgent(model, FakeProductSource()).generate(
        "fake-desk-lamp-001", "US", "en"
    )

    assert result.content.seo_title == "Adjustable LED Desk Lamp"
    assert result.execution.agent_version == "copy-seo-react-v1"
    assert result.trace is not None
    assert result.trace.model_attempts == 3
    assert [call.tool for call in result.trace.tool_calls] == [
        "get_product_facts",
        "get_seo_rules",
        "validate_copy_seo",
    ]
    assert {"get_product_facts", "get_seo_rules", "CopySeoContent"}.issubset(
        set(model.bound_tool_names)
    )


def test_model_can_skip_optional_seo_rules_tool() -> None:
    model = ScriptedToolCallingModel(
        responses=[
            tool_call(
                "get_product_facts",
                {"product_id": "fake-desk-lamp-001"},
                "product-call",
            ),
            copy_output(),
        ]
    )

    result = CopySeoReactAgent(model, FakeProductSource()).generate(
        "fake-desk-lamp-001", "US", "en"
    )

    assert result.trace is not None
    assert [call.tool for call in result.trace.tool_calls] == [
        "get_product_facts",
        "validate_copy_seo",
    ]


def test_validator_feedback_causes_one_bounded_revision() -> None:
    model = ScriptedToolCallingModel(
        responses=[
            tool_call(
                "get_product_facts",
                {"product_id": "fake-desk-lamp-001"},
                "product-call",
            ),
            copy_output(description="Includes a lifetime warranty."),
            copy_output(),
        ]
    )

    result = CopySeoReactAgent(model, FakeProductSource(), max_revisions=1).generate(
        "fake-desk-lamp-001", "US", "en"
    )

    assert result.trace is not None
    assert result.trace.revision_count == 1
    assert result.trace.model_attempts == 3
    assert [call.status for call in result.trace.tool_calls[-2:]] == [
        "FAILED",
        "SUCCEEDED",
    ]


def test_missing_product_is_reported_after_real_tool_call() -> None:
    model = ScriptedToolCallingModel(
        responses=[
            tool_call(
                "get_product_facts",
                {"product_id": "missing-product"},
                "product-call",
            ),
            copy_output(),
        ]
    )

    with pytest.raises(CopySeoAgentError) as raised:
        CopySeoReactAgent(model, FakeProductSource()).generate("missing-product", "US", "en")

    assert raised.value.error_type == "SOURCE_PRODUCT_NOT_FOUND"
    assert raised.value.trace is not None
    assert raised.value.trace.tool_calls[-1].status == "FAILED"


def test_model_cannot_bypass_required_product_facts() -> None:
    model = ScriptedToolCallingModel(responses=[copy_output()])

    with pytest.raises(CopySeoAgentError) as raised:
        CopySeoReactAgent(model, FakeProductSource(), max_revisions=0).generate(
            "fake-desk-lamp-001", "US", "en"
        )

    assert raised.value.error_type == "CONTENT_REQUIRES_HUMAN_REVIEW"
    assert raised.value.trace is not None
    assert raised.value.trace.validation_issues == ["PRODUCT_FACTS_NOT_COLLECTED"]
