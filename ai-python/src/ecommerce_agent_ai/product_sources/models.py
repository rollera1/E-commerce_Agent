from datetime import datetime
from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ProductSourceName(StrEnum):
    FAKE = "FAKE"
    CJ = "CJ"


class Money(ApiModel):
    amount: Decimal = Field(ge=0)
    currency: str = Field(min_length=3, max_length=3)


class SourceVariant(ApiModel):
    source_variant_id: str
    sku: str
    title: str
    price: Money
    inventory_available: int | None = Field(default=None, ge=0)


class SourceProduct(ApiModel):
    source: ProductSourceName
    source_product_id: str
    title: str
    description: str
    images: list[str]
    price: Money
    variants: list[SourceVariant]
    source_url: str
    fetched_at: datetime


class SourceProductPage(ApiModel):
    items: list[SourceProduct]
    page: int = Field(ge=1)
    page_size: int = Field(ge=1)
    total: int = Field(ge=0)
