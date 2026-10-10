package com.ecommerce.agent.saleschannel;

/** Shopify 商品可以发布到的销售目标。 */
public record ShopifyPublication(String id, String title, boolean autoPublish) {
}
