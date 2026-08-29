package com.ecommerce.agent.storeconnection;

import com.ecommerce.agent.integration.shopify.ShopifyProperties;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ShopifyConnectionService {

    private final ShopifyProperties properties;
    private final ShopifyStoreConnectionRepository repository;
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
        ShopifyStoreConnectionView store = repository
                .findFirstByOrderByIdAsc()
                .map(ShopifyStoreConnection::toView)
                .orElse(null);

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
        if (!isConfigured()) {
            throw new ShopifyNotConfiguredException(
                    "Shopify is not configured. Set the shop domain and app credentials first.");
        }

        ShopifyStoreConnectionManager connectionManager = connectionManagerProvider.getIfAvailable();
        if (connectionManager == null) {
            throw new ShopifyNotConfiguredException("Shopify connection is not enabled.");
        }

        ShopifyStoreDomain storeDomain;
        try {
            storeDomain = new ShopifyStoreDomain(properties.getShopDomain());
        } catch (IllegalArgumentException exception) {
            throw new ShopifyNotConfiguredException(
                    "SHOPIFY_SHOP_DOMAIN must be a valid *.myshopify.com domain.");
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
