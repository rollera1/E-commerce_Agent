package com.ecommerce.agent.integration.shopify;

/** Shopify 外部调用的错误分类。 */
public enum ShopifyErrorCode {
    // Client ID、Secret 或访问令牌无效。
    INVALID_CREDENTIALS,
    // 应用缺少执行请求所需的权限。
    INSUFFICIENT_SCOPE,
    // Shopify 返回了 GraphQL errors。
    GRAPHQL_ERROR,
    // Shopify 响应缺少业务所需字段。
    INVALID_RESPONSE,
    // 网络错误或 Shopify 服务不可用。
    REMOTE_UNAVAILABLE
}
