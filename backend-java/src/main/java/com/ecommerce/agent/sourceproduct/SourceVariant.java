package com.ecommerce.agent.sourceproduct;

public record SourceVariant(
        String sourceVariantId,
        String sku,
        String title,
        Money price,
        Integer inventoryAvailable) {}
