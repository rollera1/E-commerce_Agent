package com.ecommerce.agent.saleschannel;

/** Java 业务层访问 Shopify Admin 能力的接口。 */
public interface ShopifyAdminGateway {

    // 读取并验证指定 Shopify 店铺的基本信息。
    ShopifyShopInfo fetchShopInfo(ShopifyStoreDomain storeDomain);
}
