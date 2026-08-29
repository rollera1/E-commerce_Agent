package com.ecommerce.agent.storeconnection;

import java.time.Instant;

public record ShopifyStoreConnectionView(
        String shopifyShopId,
        String myshopifyDomain,
        String shopName,
        String primaryDomain,
        String currencyCode,
        ShopifyAuthorizationStatus authorizationStatus,
        Instant connectedAt,
        Instant lastVerifiedAt) {
}
