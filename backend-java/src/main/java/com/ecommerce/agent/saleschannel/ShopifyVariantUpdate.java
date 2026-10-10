package com.ecommerce.agent.saleschannel;

import java.math.BigDecimal;

/** 更新已存在 Shopify 变体售价的命令。 */
public record ShopifyVariantUpdate(String variantId, BigDecimal price) {
}
