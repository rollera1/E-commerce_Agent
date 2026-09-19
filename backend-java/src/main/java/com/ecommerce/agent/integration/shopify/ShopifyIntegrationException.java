package com.ecommerce.agent.integration.shopify;

/** 调用 Shopify 失败时使用的统一异常。 */
public class ShopifyIntegrationException extends RuntimeException {

    // 供 Web 层选择 HTTP 状态码的错误类型。
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
