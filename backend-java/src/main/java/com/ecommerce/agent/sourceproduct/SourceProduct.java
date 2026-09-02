package com.ecommerce.agent.sourceproduct;

import java.time.Instant;
import java.util.List;

public record SourceProduct(
        String source,
        String sourceProductId,
        String title,
        String description,
        List<String> images,
        Money price,
        List<SourceVariant> variants,
        String sourceUrl,
        Instant fetchedAt) {}
