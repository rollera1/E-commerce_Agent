package com.ecommerce.agent.storeconnection;

import com.ecommerce.agent.saleschannel.ShopifyAdminGateway;
import com.ecommerce.agent.saleschannel.ShopifyShopInfo;
import com.ecommerce.agent.saleschannel.ShopifyStoreDomain;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/** 调用 Shopify 验证店铺，并新增或刷新本地连接记录。 */
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
        // 只有真实读取到店铺信息后，才写入已连接状态。
        ShopifyShopInfo shop = shopifyAdminGateway.fetchShopInfo(storeDomain);
        Instant now = clock.instant();
        ShopifyStoreConnection connection = repository
                .findByMyshopifyDomain(shop.myshopifyDomain())
                .map(existing -> {
                    // 相同永久域名重复连接时更新原记录，避免产生重复店铺。
                    existing.refresh(shop, now);
                    return existing;
                })
                .orElseGet(() -> ShopifyStoreConnection.connected(shop, now));
        return repository.save(connection).toView();
    }
}
