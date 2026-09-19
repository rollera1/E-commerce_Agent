package com.ecommerce.agent.storeconnection;

/** 返回给前端的 Shopify 连接状态。 */
public record ShopifyConnectionStatus(
        // 配置中是否启用了 Shopify。
        boolean enabled,
        // 店铺域名和应用凭据是否完整。
        boolean configured,
        // 标准化后的配置店铺域名。
        String configuredShopDomain,
        // 当前连接阶段。
        ShopifyConnectionState state,
        // 已验证的店铺信息；尚未连接时为空。
        ShopifyStoreConnectionView store) {
}
