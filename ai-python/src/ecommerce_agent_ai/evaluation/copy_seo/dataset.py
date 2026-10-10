import json
from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path

from pydantic import BaseModel, Field

from ecommerce_agent_ai.product_sources.models import (
    Money,
    ProductSourceName,
    SourceProduct,
    SourceVariant,
)


class EvaluationSample(BaseModel):
    case_id: str
    title: str
    description: str
    price: Decimal = Field(ge=0)
    inventory: int | None = Field(default=None, ge=0)
    expected_keywords: list[str] = Field(min_length=1)
    category: str

    def to_product(self) -> SourceProduct:
        money = Money(amount=self.price, currency="USD")
        return SourceProduct(
            source=ProductSourceName.FAKE,
            source_product_id=self.case_id,
            title=self.title,
            description=self.description,
            images=[f"https://example.invalid/{self.case_id}.jpg"],
            price=money,
            variants=[
                SourceVariant(
                    source_variant_id=f"{self.case_id}-v1",
                    sku=f"EVAL-{self.case_id.upper()}",
                    title="Default",
                    price=money,
                    inventory_available=self.inventory,
                )
            ],
            source_url=f"https://example.invalid/products/{self.case_id}",
            fetched_at=datetime(2026, 9, 1, tzinfo=UTC),
        )


def load_dataset(path: Path) -> list[EvaluationSample]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    samples = [EvaluationSample.model_validate(item) for item in raw["samples"]]
    if len(samples) != 20:
        raise ValueError("copy-seo-v1 must contain exactly 20 samples")
    if len({sample.case_id for sample in samples}) != len(samples):
        raise ValueError("copy-seo-v1 case IDs must be unique")
    return samples
