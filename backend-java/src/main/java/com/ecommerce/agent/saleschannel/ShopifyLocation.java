package com.ecommerce.agent.saleschannel;

/** 可用于写入库存的 Shopify Location。 */
public record ShopifyLocation(String id, String name, boolean active) {
}
