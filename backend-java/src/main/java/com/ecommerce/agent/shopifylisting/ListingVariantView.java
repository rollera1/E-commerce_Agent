package com.ecommerce.agent.shopifylisting;

import java.math.BigDecimal;

public record ListingVariantView(
        String sourceVariantId, String sku, String optionValue,
        BigDecimal sellingPrice, int sourceInventory, String shopifyVariantId) {
}
