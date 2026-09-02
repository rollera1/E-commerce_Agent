package com.ecommerce.agent.sourceproduct;

import java.util.List;

public record SourceProductPage(
        List<SourceProduct> items,
        int page,
        int pageSize,
        int total) {}
