package com.ecommerce.agent.productselection;

import com.ecommerce.agent.sourceproduct.Money;
import com.ecommerce.agent.sourceproduct.SourceVariant;
import java.util.List;

/** 选品 Agent 返回给商家确认的候选商品。 */
public record SelectionCandidate(
        String sourceProductId,
        String source,
        String title,
        String description,
        List<String> images,
        Money price,
        List<SourceVariant> variants,
        String inventoryStatus,
        String sourceUrl,
        String fetchedAt,
        String matchedQuery,
        Integer inventoryTotal) {
    public SelectionCandidate(String sourceProductId, String source, String title,
            String description, List<String> images, Money price, List<SourceVariant> variants,
            String inventoryStatus, String sourceUrl, String fetchedAt, String matchedQuery) {
        this(sourceProductId, source, title, description, images, price, variants,
                inventoryStatus, sourceUrl, fetchedAt, matchedQuery, null);
    }
}
