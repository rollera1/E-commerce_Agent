package com.ecommerce.agent.saleschannel;

import java.util.List;

/** 创建或更新 Shopify 草稿所需的业务字段。 */
public record ShopifyProductCommand(
        String title,
        String descriptionHtml,
        String seoTitle,
        String seoDescription,
        List<String> tags,
        List<String> imageUrls,
        List<String> optionValues) {
}
