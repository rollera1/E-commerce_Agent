package com.ecommerce.agent.integration.shopify;

public final class ShopifyClientCredentials {

    private final String clientId;
    private final String clientSecret;

    public ShopifyClientCredentials(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Shopify client ID is required");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Shopify client secret is required");
        }
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    @Override
    public String toString() {
        return "ShopifyClientCredentials[redacted]";
    }
}
