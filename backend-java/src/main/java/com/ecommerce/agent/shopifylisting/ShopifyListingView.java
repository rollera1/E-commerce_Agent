package com.ecommerce.agent.shopifylisting;

import java.util.List;

public record ShopifyListingView(
        String generationId,
        ReviewMode reviewMode,
        PublishMode publishMode,
        String locationId,
        String publicationId,
        boolean autoPublishAuthorized,
        ListingStatus status,
        ListingStep currentStep,
        String shopifyProductId,
        List<String> imageUrls,
        boolean mediaReady,
        String lastErrorCode,
        String lastErrorMessage,
        List<ListingVariantView> variants) {
}
