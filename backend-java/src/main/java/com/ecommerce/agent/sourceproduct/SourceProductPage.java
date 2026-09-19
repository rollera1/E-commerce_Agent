package com.ecommerce.agent.sourceproduct;

import java.util.List;

/** 来源商品分页查询结果。 */
public record SourceProductPage(
        // 当前页商品。
        List<SourceProduct> items,
        // 当前页码，从 1 开始。
        int page,
        // 每页最大商品数。
        int pageSize,
        // 符合条件的商品总数。
        int total) {}
