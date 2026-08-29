package com.ecommerce.agent.saleschannel;

public record ShopifyShopInfo(
        String shopifyId,
        String name,
        String myshopifyDomain,
        String primaryDomain,
        String currencyCode) {
}
