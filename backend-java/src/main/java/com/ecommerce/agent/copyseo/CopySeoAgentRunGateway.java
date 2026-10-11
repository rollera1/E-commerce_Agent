package com.ecommerce.agent.copyseo;

/** Java 控制层提交、查询和取消 Python Agent Run 的接口。 */
public interface CopySeoAgentRunGateway {
    AgentRunResult submit(
            String generationId,
            int attempt,
            String sourceProductId,
            String targetMarket,
            String language);

    AgentRunResult get(String runId);

    AgentRunResult cancel(String runId);
}
