package com.ecommerce.agent.storeconnection;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopifyStoreConnectionRepository
        extends JpaRepository<ShopifyStoreConnection, Long> {

    Optional<ShopifyStoreConnection> findByMyshopifyDomain(String myshopifyDomain);

    Optional<ShopifyStoreConnection> findFirstByOrderByIdAsc();
}
