package com.ecommerce.agent.storeconnection;

public record ShopifyConnectionStatus(
        boolean enabled,
        boolean configured,
        String configuredShopDomain,
        ShopifyConnectionState state,
        ShopifyStoreConnectionView store) {
}
