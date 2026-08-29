package com.ecommerce.agent.integration.shopify;

import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;

public interface ShopifyAccessTokenProvider {

    String getAccessToken(ShopifyStoreDomain storeDomain);
}
