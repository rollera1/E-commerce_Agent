package com.ecommerce.agent.productselection;

import java.util.List;
import java.util.Map;

/** Python 选品 Agent 的结构化响应。 */
public record AgentProductSelection(
        String selectionId,
        String query,
        String matchedQuery,
        String outcome,
        List<SelectionCandidate> candidates,
        Map<String, Object> trace) {
}
