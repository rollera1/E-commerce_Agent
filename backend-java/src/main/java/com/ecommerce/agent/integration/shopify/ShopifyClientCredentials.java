package com.ecommerce.agent.integration.shopify;

/** Shopify 应用的 Client ID 和 Client Secret。 */
public final class ShopifyClientCredentials {

    // Shopify Dev Dashboard 提供的应用 ID。
    private final String clientId;
    // Shopify Dev Dashboard 提供的应用密钥。
    private final String clientSecret;

    public ShopifyClientCredentials(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Shopify Client ID 不能为空");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Shopify Client Secret 不能为空");
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
        // 防止日志或调试输出意外暴露真实凭据。
        return "Shopify客户端凭据[已隐藏]";
    }
}
