package com.ecommerce.agent.shopifylisting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopifyListingVariantRepository extends JpaRepository<ShopifyListingVariant, Long> {
    List<ShopifyListingVariant> findByGenerationIdOrderByIdAsc(String generationId);
    void deleteByGenerationId(String generationId);
}
