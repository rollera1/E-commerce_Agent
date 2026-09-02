from enum import StrEnum

from pydantic import Field

from ecommerce_agent_ai.product_sources.models import ApiModel


class GenerationStatus(StrEnum):
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class ReviewStatus(StrEnum):
    PENDING_REVIEW = "PENDING_REVIEW"
    ACCEPTED = "ACCEPTED"
    REJECTED = "REJECTED"


class CopySeoGenerationRequest(ApiModel):
    source_product_id: str = Field(min_length=1, max_length=200)
    target_market: str = Field(pattern=r"^[A-Z]{2}$")
    language: str = Field(pattern=r"^[a-z]{2}$")


class CopySeoContent(ApiModel):
    title: str = Field(min_length=1, max_length=255)
    bullet_points: list[str] = Field(min_length=1, max_length=8)
    description: str = Field(min_length=1)
    seo_title: str = Field(min_length=1, max_length=70)
    meta_description: str = Field(min_length=1, max_length=160)
    tags: list[str] = Field(min_length=1, max_length=20)


class AgentExecution(ApiModel):
    agent: str
    agent_version: str
    prompt_version: str
    model: str
    duration_ms: int = Field(ge=0)


class CopySeoGeneration(ApiModel):
    generation_id: str
    source_product_id: str
    status: GenerationStatus
    review_status: ReviewStatus
    content: CopySeoContent
    execution: AgentExecution
