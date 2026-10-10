from decimal import Decimal

import pytest

from ecommerce_agent_ai.agents.copy_seo.fake import FakeCopySeoAgent
from ecommerce_agent_ai.agents.copy_seo.tools import (
    GetProductFactsTool,
    InventoryStatus,
    RuleBasedContentValidator,
    product_facts,
    seo_rules,
)
from ecommerce_agent_ai.product_sources.fake import FakeProductSource
from ecommerce_agent_ai.product_sources.models import Money


def product():
    result = FakeProductSource().get_product("fake-desk-lamp-001")
    assert result is not None
    return result


@pytest.mark.parametrize("language,name", [("en", "英文"), ("zh", "简体中文")])
def test_seo_rules_explicitly_apply_requested_language_to_all_copy_fields(language, name):
    rules = seo_rules("US", language)
    assert rules["language"] == language
    assert rules["outputLanguage"] == name
    assert "标签统一使用指定输出语言" in rules["requirements"][0]


def test_product_facts_marks_unknown_inventory_without_guessing() -> None:
    source_product = product()
    variants = [
        variant.model_copy(update={"inventory_available": None})
        for variant in source_product.variants
    ]

    facts = product_facts(source_product.model_copy(update={"variants": variants}), "US")

    assert facts["inventory"]["status"] == InventoryStatus.UNKNOWN
    assert facts["inventory"]["knownUnits"] == 0
    assert facts["inventory"]["unknownVariantCount"] == 1


def test_product_facts_rejects_mixed_currencies_instead_of_guessing() -> None:
    source_product = product()
    variants = [
        source_product.variants[0].model_copy(
            update={"price": Money(amount=Decimal("20.00"), currency="EUR")}
        )
    ]

    with pytest.raises(ValueError, match="PRICE_CURRENCY_MISMATCH"):
        product_facts(source_product.model_copy(update={"variants": variants}), "US")


def test_get_product_facts_tool_reads_standardized_product_source() -> None:
    traces = []
    facts_tool = GetProductFactsTool(
        FakeProductSource(),
        "fake-desk-lamp-001",
        "US",
        traces=traces,
    )

    facts = facts_tool.execute("fake-desk-lamp-001")

    assert facts["sourceProductId"] == "fake-desk-lamp-001"
    assert facts_tool.last_product is not None
    assert facts_tool.failure is None
    assert traces[0].tool == "get_product_facts"
    assert traces[0].status.value == "SUCCEEDED"


def test_get_product_facts_tool_rejects_unexpected_product_id() -> None:
    facts_tool = GetProductFactsTool(
        FakeProductSource(),
        "fake-desk-lamp-001",
        "US",
    )

    result = facts_tool.execute("another-product")

    assert result == {"found": False, "error": "PRODUCT_ID_MISMATCH"}
    assert facts_tool.last_product is None
    assert facts_tool.failure == ("PRODUCT_ID_MISMATCH", False)


def test_get_product_facts_tool_exposes_langchain_tool_adapter() -> None:
    facts_tool = GetProductFactsTool(
        FakeProductSource(),
        "fake-desk-lamp-001",
        "US",
    )

    result = facts_tool.as_langchain_tool().invoke({"product_id": "fake-desk-lamp-001"})

    assert result["sourceProductId"] == "fake-desk-lamp-001"


def test_validator_rejects_availability_claim_when_inventory_is_unknown() -> None:
    source_product = product()
    variants = [
        variant.model_copy(update={"inventory_available": None})
        for variant in source_product.variants
    ]
    source_product = source_product.model_copy(update={"variants": variants})
    generated = FakeCopySeoAgent().generate("fake-desk-lamp-001", "US", "en")
    content = generated.content.model_copy(
        update={"description": "Available now for your home office."}
    )

    result = RuleBasedContentValidator().validate(content, source_product)

    assert result.passed is False
    assert result.issues == ["UNSUPPORTED_AVAILABILITY:available now"]
