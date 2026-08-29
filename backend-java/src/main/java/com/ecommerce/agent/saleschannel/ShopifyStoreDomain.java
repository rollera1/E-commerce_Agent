package com.ecommerce.agent.saleschannel;

import java.util.Locale;
import java.util.regex.Pattern;

public record ShopifyStoreDomain(String value) {

    private static final Pattern VALID_DOMAIN = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.myshopify\\.com$");

    public ShopifyStoreDomain {
        if (value == null) {
            throw new IllegalArgumentException("Shopify store domain is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID_DOMAIN.matcher(value).matches()) {
            throw new IllegalArgumentException("Shopify store domain must be a valid *.myshopify.com host");
        }
    }
}
