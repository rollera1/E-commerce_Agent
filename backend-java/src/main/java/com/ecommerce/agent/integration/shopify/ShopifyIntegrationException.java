package com.ecommerce.agent.integration.shopify;

public class ShopifyIntegrationException extends RuntimeException {

    private final ShopifyErrorCode code;

    public ShopifyIntegrationException(ShopifyErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ShopifyIntegrationException(ShopifyErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ShopifyErrorCode getCode() {
        return code;
    }
}
