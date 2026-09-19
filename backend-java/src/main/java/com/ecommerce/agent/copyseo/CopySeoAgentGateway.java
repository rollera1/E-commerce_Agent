package com.ecommerce.agent.copyseo;

/** Java 业务层调用文案与 SEO Agent 的接口。 */
public interface CopySeoAgentGateway {
    AgentGeneration generate(String sourceProductId, String targetMarket, String language);
}
