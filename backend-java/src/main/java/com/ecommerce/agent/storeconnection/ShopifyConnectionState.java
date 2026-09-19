package com.ecommerce.agent.storeconnection;

/** 前端展示的 Shopify 连接阶段。 */
public enum ShopifyConnectionState {
    // 尚未提供完整配置。
    NOT_CONFIGURED,
    // 配置完整，但还没有验证成功的连接记录。
    DISCONNECTED,
    // 已通过真实 Shopify 查询并保存连接记录。
    CONNECTED
}
