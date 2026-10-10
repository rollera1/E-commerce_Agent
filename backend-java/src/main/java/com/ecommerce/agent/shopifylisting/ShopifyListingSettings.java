package com.ecommerce.agent.shopifylisting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record ShopifyListingSettings(
        @NotNull ReviewMode reviewMode,
        @NotNull PublishMode publishMode,
        @NotBlank String locationId,
        @NotBlank String publicationId,
        boolean autoPublishAuthorized,
        @NotEmpty List<@Pattern(regexp = "^https://.+", message = "图片地址必须使用 HTTPS") String> imageUrls,
        @NotEmpty List<@Valid VariantPrice> variantPrices) {
    public ShopifyListingSettings {
        imageUrls = imageUrls == null ? null : List.copyOf(imageUrls);
        variantPrices = variantPrices == null ? null : List.copyOf(variantPrices);
    }
}
