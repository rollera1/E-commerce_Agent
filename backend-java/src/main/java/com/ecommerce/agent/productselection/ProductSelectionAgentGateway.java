package com.ecommerce.agent.productselection;

/** 调用选品 Agent 的业务接口。 */
public interface ProductSelectionAgentGateway {
    AgentProductSelection search(String query, int limit);
}
