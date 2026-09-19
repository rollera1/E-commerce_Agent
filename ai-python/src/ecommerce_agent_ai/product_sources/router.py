from typing import Annotated

from fastapi import APIRouter, HTTPException, Query

from ecommerce_agent_ai.product_sources.factory import build_product_source
from ecommerce_agent_ai.product_sources.models import SourceProduct, SourceProductPage
from ecommerce_agent_ai.product_sources.port import ProductSource

router = APIRouter(prefix="/internal/product-sources", tags=["product-sources"])
product_source: ProductSource = build_product_source()


@router.get("/products", response_model=SourceProductPage)
def list_source_products(
    keyword: Annotated[str | None, Query(max_length=100)] = None,
    page: Annotated[int, Query(ge=1)] = 1,
    page_size: Annotated[int, Query(alias="pageSize", ge=1, le=100)] = 20,
) -> SourceProductPage:
    return product_source.list_products(keyword, page, page_size)


@router.get("/products/{source_product_id}", response_model=SourceProduct)
def get_source_product(source_product_id: str) -> SourceProduct:
    product = product_source.get_product(source_product_id)
    if product is None:
        raise HTTPException(
            status_code=404,
            detail={
                "errorCode": "SOURCE_PRODUCT_NOT_FOUND",
                "message": "Source product was not found",
            },
        )
    return product
