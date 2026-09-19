package com.ecommerce.agent.storeconnection;

import java.time.Instant;

/** 返回给前端的已连接 Shopify 店铺信息。 */
public record ShopifyStoreConnectionView(
        // Shopify 全局店铺 ID。
        String shopifyShopId,
        // Shopify 永久店铺域名。
        String myshopifyDomain,
        // 店铺名称。
        String shopName,
        // 商家当前主域名。
        String primaryDomain,
        // 店铺币种代码。
        String currencyCode,
        // 授权状态。
        ShopifyAuthorizationStatus authorizationStatus,
        // 首次连接时间。
        Instant connectedAt,
        // 最近一次真实验证时间。
        Instant lastVerifiedAt) {
}
