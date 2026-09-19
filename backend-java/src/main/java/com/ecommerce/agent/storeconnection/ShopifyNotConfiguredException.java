package com.ecommerce.agent.storeconnection;

/** Shopify 未启用或缺少必要配置。 */
public class ShopifyNotConfiguredException extends RuntimeException {

    public ShopifyNotConfiguredException(String message) {
        super(message);
    }
}
