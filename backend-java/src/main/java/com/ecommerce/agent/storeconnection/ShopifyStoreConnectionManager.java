package com.ecommerce.agent.storeconnection;

import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.saleschannel.ShopifyShopInfo;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

public class ShopifyStoreConnectionManager {

    private final ShopifyAdminGateway shopifyAdminGateway;
    private final ShopifyStoreConnectionRepository repository;
    private final Clock clock;

    public ShopifyStoreConnectionManager(
            ShopifyAdminGateway shopifyAdminGateway,
            ShopifyStoreConnectionRepository repository,
            Clock clock) {
        this.shopifyAdminGateway = shopifyAdminGateway;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ShopifyStoreConnectionView connect(ShopifyStoreDomain storeDomain) {
        ShopifyShopInfo shop = shopifyAdminGateway.fetchShopInfo(storeDomain);
        Instant now = clock.instant();
        ShopifyStoreConnection connection = repository
                .findByMyshopifyDomain(shop.myshopifyDomain())
                .map(existing -> {
                    existing.refresh(shop, now);
                    return existing;
                })
                .orElseGet(() -> ShopifyStoreConnection.connected(shop, now));
        return repository.save(connection).toView();
    }
}
