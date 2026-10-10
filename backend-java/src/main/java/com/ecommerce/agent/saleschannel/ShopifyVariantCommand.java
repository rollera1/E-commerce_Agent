package com.ecommerce.agent.saleschannel;

import java.math.BigDecimal;

/** 创建 Shopify 变体、售价和初始库存的命令。 */
public record ShopifyVariantCommand(
        String sourceVariantId,
        String optionValue,
        String sku,
        BigDecimal price,
        int inventoryQuantity) {
}
