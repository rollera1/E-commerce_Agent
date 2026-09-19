package com.ecommerce.agent.integration.shopify;

import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;

/** 获取指定 Shopify 店铺短期访问令牌的接口。 */
public interface ShopifyAccessTokenProvider {

    String getAccessToken(ShopifyStoreDomain storeDomain);
}
