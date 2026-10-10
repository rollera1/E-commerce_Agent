package com.ecommerce.agent.shopifylisting;

public class ListingNotFoundException extends RuntimeException {
    public ListingNotFoundException(String generationId) {
        super("Shopify 上架任务不存在：" + generationId);
    }
}
