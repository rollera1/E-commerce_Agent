package com.ecommerce.agent.storeconnection;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Shopify 店铺连接记录的数据访问接口。 */
public interface ShopifyStoreConnectionRepository
        extends JpaRepository<ShopifyStoreConnection, Long> {

    // 按永久店铺域名查找记录，用于重复连接时更新而不是新增。
    Optional<ShopifyStoreConnection> findByMyshopifyDomain(String myshopifyDomain);

    // 当前单店 MVP 读取第一条店铺连接记录。
    Optional<ShopifyStoreConnection> findFirstByOrderByIdAsc();
}
