from datetime import datetime
from enum import StrEnum

from pydantic import Field

from ecommerce_agent_ai.product_sources.models import ApiModel, Money, SourceVariant


class SearchDecision(StrEnum):
    RETURN_RESULTS = "RETURN_RESULTS"
    REWRITE_QUERY = "REWRITE_QUERY"
    NO_RESULTS = "NO_RESULTS"


class SelectionOutcome(StrEnum):
    CANDIDATES_FOUND = "CANDIDATES_FOUND"
    NO_CANDIDATES = "NO_CANDIDATES"


class InventoryStatus(StrEnum):
    IN_STOCK = "IN_STOCK"
    OUT_OF_STOCK = "OUT_OF_STOCK"
    UNKNOWN = "UNKNOWN"


class ProductSelectionRequest(ApiModel):
    query: str = Field(min_length=1, max_length=100)
    limit: int = Field(default=10, ge=1, le=100)


class SearchQueryPlan(ApiModel):
    keyword: str = Field(min_length=1, max_length=100)


class SearchAssessment(ApiModel):
    decision: SearchDecision
    reason: str = Field(min_length=1, max_length=300)
    rewritten_query: str | None = Field(default=None, min_length=1, max_length=100)


class CandidateProduct(ApiModel):
    source_product_id: str
    source: str
    title: str
    description: str
    images: list[str]
    price: Money
    variants: list[SourceVariant]
    inventory_status: InventoryStatus
    source_url: str
    fetched_at: datetime
    matched_query: str
    inventory_total: int | None = Field(default=None, ge=0)


class SelectionSearchTrace(ApiModel):
    query: str
    result_count: int = Field(ge=0)
    duration_ms: int = Field(ge=0)


class ProductSelectionTrace(ApiModel):
    steps: list[str]
    searches: list[SelectionSearchTrace]
    search_count: int = Field(ge=0, le=2)
    rewrite_count: int = Field(ge=0, le=1)
    model_attempts: int = Field(ge=0)
    stop_reason: str
    next_action: str


class ProductSelectionResult(ApiModel):
    selection_id: str
    query: str
    matched_query: str
    outcome: SelectionOutcome
    candidates: list[CandidateProduct]
    trace: ProductSelectionTrace
