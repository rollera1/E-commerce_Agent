package com.ecommerce.agent.saleschannel;

/** 从 Shopify Admin API 读取的店铺基本信息。 */
public record ShopifyShopInfo(
        // Shopify 全局店铺 ID。
        String shopifyId,
        // 店铺显示名称。
        String name,
        // Shopify 提供的永久 myshopify.com 域名。
        String myshopifyDomain,
        // 商家当前使用的主域名。
        String primaryDomain,
        // 店铺使用的三位币种代码。
        String currencyCode) {
}
