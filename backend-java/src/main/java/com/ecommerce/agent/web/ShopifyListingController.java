package com.ecommerce.agent.web;

import com.ecommerce.agent.shopifylisting.ShopifyListingService;
import com.ecommerce.agent.shopifylisting.ShopifyListingSettings;
import com.ecommerce.agent.shopifylisting.ShopifyListingView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shopify 上架任务查询、恢复、发布和退回编辑接口。 */
@RestController
@RequestMapping("/api/copy-seo-generations/{generationId}/shopify-listing")
public class ShopifyListingController {
    private final ShopifyListingService service;

    public ShopifyListingController(ShopifyListingService service) { this.service = service; }

    @GetMapping
    public ShopifyListingView get(@PathVariable String generationId) { return service.get(generationId); }

    @PutMapping("/settings")
    public ShopifyListingView settings(
            @PathVariable String generationId,
            @Valid @RequestBody ShopifyListingSettings settings) {
        return service.updateSettings(generationId, settings);
    }

    @PostMapping("/publish")
    public ShopifyListingView publish(@PathVariable String generationId) { return service.publish(generationId); }

    @PostMapping("/draft")
    public ShopifyListingView draft(
            @PathVariable String generationId,
            @Valid @RequestBody ShopifyListingSettings settings) {
        return service.createAcceptedDraft(generationId, settings);
    }

    @PostMapping("/retry")
    public ShopifyListingView retry(@PathVariable String generationId) { return service.retry(generationId); }

    @PostMapping("/reopen")
    public ShopifyListingView reopen(@PathVariable String generationId) { return service.reopen(generationId); }
}
