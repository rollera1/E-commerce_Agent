package com.ecommerce.agent.storeconnection;

import com.ecommerce.agent.integration.shopify.ShopifyProperties;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 查询 Shopify 连接状态并处理主动连接请求。 */
@Service
public class ShopifyConnectionService {

    // application.yml 和环境变量中的 Shopify 配置。
    private final ShopifyProperties properties;
    // 已验证店铺连接的数据访问接口。
    private final ShopifyStoreConnectionRepository repository;
    // Shopify 未启用时 Manager Bean 不存在，因此使用可选提供器获取。
    private final ObjectProvider<ShopifyStoreConnectionManager> connectionManagerProvider;

    public ShopifyConnectionService(
            ShopifyProperties properties,
            ShopifyStoreConnectionRepository repository,
            ObjectProvider<ShopifyStoreConnectionManager> connectionManagerProvider) {
        this.properties = properties;
        this.repository = repository;
        this.connectionManagerProvider = connectionManagerProvider;
    }

    public ShopifyConnectionStatus status() {
        boolean configured = isConfigured();
        // 当前 MVP 只支持一个商家和一个店铺，因此读取第一条连接记录。
        ShopifyStoreConnectionView store = repository
                .findFirstByOrderByIdAsc()
                .map(ShopifyStoreConnection::toView)
                .orElse(null);

        // 数据库中有已验证记录时优先显示已连接，否则根据配置完整度区分状态。
        ShopifyConnectionState state = store != null
                ? ShopifyConnectionState.CONNECTED
                : configured
                        ? ShopifyConnectionState.DISCONNECTED
                        : ShopifyConnectionState.NOT_CONFIGURED;

        return new ShopifyConnectionStatus(
                properties.isEnabled(),
                configured,
                normalizedDomain(),
                state,
                store);
    }

    public ShopifyConnectionStatus connect() {
        // 未启用或缺少域名、Client ID、Secret 时不调用 Shopify。
        if (!isConfigured()) {
            throw new ShopifyNotConfiguredException(
                    "Shopify 尚未配置，请先设置店铺域名和应用凭据");
        }

        ShopifyStoreConnectionManager connectionManager = connectionManagerProvider.getIfAvailable();
        if (connectionManager == null) {
            throw new ShopifyNotConfiguredException("Shopify 连接功能尚未启用");
        }

        ShopifyStoreDomain storeDomain;
        try {
            // 值对象会标准化域名并拒绝非 myshopify.com 地址。
            storeDomain = new ShopifyStoreDomain(properties.getShopDomain());
        } catch (IllegalArgumentException exception) {
            throw new ShopifyNotConfiguredException(
                    "SHOPIFY_SHOP_DOMAIN 必须是有效的 *.myshopify.com 域名");
        }

        ShopifyStoreConnectionView store = connectionManager.connect(storeDomain);
        return new ShopifyConnectionStatus(
                true,
                true,
                storeDomain.value(),
                ShopifyConnectionState.CONNECTED,
                store);
    }

    private boolean isConfigured() {
        return properties.isEnabled()
                && hasText(properties.getShopDomain())
                && hasText(properties.getClientId())
                && hasText(properties.getClientSecret());
    }

    private String normalizedDomain() {
        return hasText(properties.getShopDomain())
                ? properties.getShopDomain().trim().toLowerCase(Locale.ROOT)
                : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
