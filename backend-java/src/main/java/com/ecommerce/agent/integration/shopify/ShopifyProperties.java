package com.ecommerce.agent.integration.shopify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** shopify 配置项的 Java 映射对象。 */
@ConfigurationProperties(prefix = "shopify")
public class ShopifyProperties {

    // 是否启用真实 Shopify 连接。
    private boolean enabled;
    // 目标开发店铺的 *.myshopify.com 域名。
    private String shopDomain = "";
    // Shopify Admin API 版本。
    private String apiVersion = "2026-07";
    // Shopify 应用 Client ID。
    private String clientId = "";
    // Shopify 应用 Client Secret。
    private String clientSecret = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getShopDomain() {
        return shopDomain;
    }

    public void setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Override
    public String toString() {
        // 配置对象可能被日志打印，因此始终隐藏 Client ID 和 Secret。
        return "Shopify配置[已启用=%s, 店铺域名=%s, API版本=%s, 客户端凭据=已隐藏]"
                .formatted(enabled, shopDomain, apiVersion);
    }

}
