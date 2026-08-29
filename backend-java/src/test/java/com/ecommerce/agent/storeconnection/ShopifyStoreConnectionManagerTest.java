package com.ecommerce.agent.storeconnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.saleschannel.ShopifyShopInfo;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShopifyStoreConnectionManagerTest {

    private static final Instant NOW = Instant.parse("2026-08-29T06:00:00Z");
    private static final ShopifyStoreDomain DOMAIN =
            new ShopifyStoreDomain("sample-store.myshopify.com");

    private ShopifyAdminGateway gateway;
    private ShopifyStoreConnectionRepository repository;
    private ShopifyStoreConnectionManager manager;

    @BeforeEach
    void setUp() {
        gateway = mock(ShopifyAdminGateway.class);
        repository = mock(ShopifyStoreConnectionRepository.class);
        manager = new ShopifyStoreConnectionManager(
                gateway,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(gateway.fetchShopInfo(DOMAIN)).thenReturn(shopInfo("Sample Store"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAConnectionWithoutPersistingSecrets() {
        when(repository.findByMyshopifyDomain(DOMAIN.value())).thenReturn(Optional.empty());

        var result = manager.connect(DOMAIN);

        assertThat(result.shopName()).isEqualTo("Sample Store");
        assertThat(result.authorizationStatus()).isEqualTo(ShopifyAuthorizationStatus.CONNECTED);
        assertThat(result.connectedAt()).isEqualTo(NOW);
        assertThat(result.lastVerifiedAt()).isEqualTo(NOW);
        verify(repository).save(any(ShopifyStoreConnection.class));
    }

    @Test
    void refreshesTheExistingDomainInsteadOfCreatingADuplicate() {
        Instant firstConnection = NOW.minusSeconds(3600);
        var existing = ShopifyStoreConnection.connected(shopInfo("Old Name"), firstConnection);
        when(repository.findByMyshopifyDomain(DOMAIN.value())).thenReturn(Optional.of(existing));

        var result = manager.connect(DOMAIN);

        var saved = ArgumentCaptor.forClass(ShopifyStoreConnection.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(result.shopName()).isEqualTo("Sample Store");
        assertThat(result.connectedAt()).isEqualTo(firstConnection);
        assertThat(result.lastVerifiedAt()).isEqualTo(NOW);
        verify(repository, never()).delete(any());
    }

    private static ShopifyShopInfo shopInfo(String name) {
        return new ShopifyShopInfo(
                "gid://shopify/Shop/123",
                name,
                DOMAIN.value(),
                "shop.example.com",
                "USD");
    }
}
