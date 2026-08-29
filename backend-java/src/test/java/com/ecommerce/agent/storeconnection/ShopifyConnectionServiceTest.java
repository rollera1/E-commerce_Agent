package com.ecommerce.agent.storeconnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.agent.integration.shopify.ShopifyProperties;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ShopifyConnectionServiceTest {

    private final ShopifyStoreConnectionRepository repository =
            mock(ShopifyStoreConnectionRepository.class);
    private final ShopifyStoreConnectionManager manager =
            mock(ShopifyStoreConnectionManager.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ShopifyStoreConnectionManager> managerProvider =
            mock(ObjectProvider.class);
    private final ShopifyProperties properties = new ShopifyProperties();
    private final ShopifyConnectionService service =
            new ShopifyConnectionService(properties, repository, managerProvider);

    @BeforeEach
    void setUp() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(managerProvider.getIfAvailable()).thenReturn(manager);
    }

    @Test
    void reportsNotConfiguredWithoutSecrets() {
        ShopifyConnectionStatus status = service.status();

        assertThat(status.enabled()).isFalse();
        assertThat(status.configured()).isFalse();
        assertThat(status.state()).isEqualTo(ShopifyConnectionState.NOT_CONFIGURED);
        assertThat(status.store()).isNull();
    }

    @Test
    void reportsDisconnectedOnceCompleteConfigurationExists() {
        configure("Sample-Store.myshopify.com");

        ShopifyConnectionStatus status = service.status();

        assertThat(status.configured()).isTrue();
        assertThat(status.configuredShopDomain()).isEqualTo("sample-store.myshopify.com");
        assertThat(status.state()).isEqualTo(ShopifyConnectionState.DISCONNECTED);
    }

    @Test
    void connectsOnlyToTheConfiguredStore() {
        configure("sample-store.myshopify.com");
        Instant now = Instant.parse("2026-08-29T05:00:00Z");
        ShopifyStoreConnectionView store = new ShopifyStoreConnectionView(
                "gid://shopify/Shop/123",
                "sample-store.myshopify.com",
                "Sample Store",
                "shop.example.com",
                "USD",
                ShopifyAuthorizationStatus.CONNECTED,
                now,
                now);
        when(manager.connect(new ShopifyStoreDomain("sample-store.myshopify.com")))
                .thenReturn(store);

        ShopifyConnectionStatus status = service.connect();

        assertThat(status.state()).isEqualTo(ShopifyConnectionState.CONNECTED);
        assertThat(status.store()).isEqualTo(store);
        verify(manager).connect(new ShopifyStoreDomain("sample-store.myshopify.com"));
    }

    @Test
    void rejectsAnUnsafeConfiguredDomainBeforeCallingShopify() {
        configure("attacker.example.com");

        assertThatThrownBy(service::connect)
                .isInstanceOf(ShopifyNotConfiguredException.class)
                .hasMessageContaining("*.myshopify.com");
    }

    private void configure(String domain) {
        properties.setEnabled(true);
        properties.setShopDomain(domain);
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
    }
}
