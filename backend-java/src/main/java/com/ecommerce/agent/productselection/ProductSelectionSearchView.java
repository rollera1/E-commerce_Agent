package com.ecommerce.agent.productselection;

import java.util.List;
import java.util.Map;

/** 供前端展示并选择候选商品的查询结果。 */
public record ProductSelectionSearchView(
        String selectionId,
        String query,
        String matchedQuery,
        String outcome,
        ProductSelectionStatus status,
        List<SelectionCandidate> candidates,
        Map<String, Object> trace) {
}
