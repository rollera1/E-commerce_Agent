from enum import StrEnum

from pydantic import Field

from ecommerce_agent_ai.product_sources.models import ApiModel


# 文案生成状态。
class GenerationStatus(StrEnum):
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


# 人工审核状态。
class ReviewStatus(StrEnum):
    PENDING_REVIEW = "PENDING_REVIEW"
    ACCEPTED = "ACCEPTED"
    REJECTED = "REJECTED"


class AgentOutcome(StrEnum):
    COMPLETED = "COMPLETED"
    NEEDS_HUMAN_INPUT = "NEEDS_HUMAN_INPUT"


class ToolCallStatus(StrEnum):
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class CopySeoGenerationRequest(ApiModel):
    source_product_id: str = Field(min_length=1, max_length=200)
    target_market: str = Field(pattern=r"^[A-Z]{2}$")
    language: str = Field(pattern=r"^[a-z]{2}$")


# Agent 生成的文案与 SEO 内容。
class CopySeoContent(ApiModel):
    title: str = Field(min_length=1, max_length=255)
    bullet_points: list[str] = Field(min_length=1, max_length=8)
    description: str = Field(min_length=1)
    seo_title: str = Field(min_length=1, max_length=70)
    meta_description: str = Field(min_length=1, max_length=160)
    tags: list[str] = Field(min_length=1, max_length=20)


# Agent 的基本执行信息。
class AgentExecution(ApiModel):
    agent: str
    agent_version: str
    prompt_version: str
    model: str
    duration_ms: int = Field(ge=0)


class ToolCallTrace(ApiModel):
    tool: str
    status: ToolCallStatus
    duration_ms: int = Field(ge=0)
    summary: dict[str, str | int | bool | None] = Field(default_factory=dict)
    error_type: str | None = None


# Agent 的工具调用和编排轨迹。
class AgentTrace(ApiModel):
    current_step: str
    outcome: AgentOutcome
    steps: list[str] = Field(default_factory=list)
    tool_calls: list[ToolCallTrace] = Field(default_factory=list)
    model_attempts: int = Field(default=0, ge=0)
    revision_count: int = Field(default=0, ge=0)
    validation_issues: list[str] = Field(default_factory=list)
    next_action: str | None = None


# 一次完整的文案生成结果。
class CopySeoGeneration(ApiModel):
    generation_id: str
    source_product_id: str
    status: GenerationStatus
    review_status: ReviewStatus
    content: CopySeoContent
    execution: AgentExecution
    trace: AgentTrace | None = None
