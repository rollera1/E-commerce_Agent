package com.ecommerce.agent.web;

import com.ecommerce.agent.saleschannel.ShopifyLocation;
import com.ecommerce.agent.saleschannel.ShopifyPublication;
import com.ecommerce.agent.shopifylisting.ShopifyListingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 向前端提供脱敏后的 Shopify Location 和 Publication。 */
@RestController
@RequestMapping("/api/shopify")
public class ShopifyCatalogController {
    private final ShopifyListingService listingService;

    public ShopifyCatalogController(ShopifyListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping("/locations")
    public List<ShopifyLocation> locations() { return listingService.locations(); }

    @GetMapping("/publications")
    public List<ShopifyPublication> publications() { return listingService.publications(); }
}
