package com.ecommerce.agent.sourceproduct;

/** 来源商品的一个可购买变体。 */
public record SourceVariant(
        // 变体在来源平台中的 ID。
        String sourceVariantId,
        // 变体 SKU。
        String sku,
        // 变体名称，例如颜色或尺寸组合。
        String title,
        // 变体价格和币种。
        Money price,
        // 可用库存；来源未提供时为空。
        Integer inventoryAvailable) {}
