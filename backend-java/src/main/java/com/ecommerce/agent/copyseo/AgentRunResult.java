package com.ecommerce.agent.copyseo;

/** Java 轮询 Python Agent Run 时使用的稳定结果。 */
public record AgentRunResult(
        String runId,
        AgentRunStatus status,
        AgentGeneration output,
        AgentRunFailure failure) {
}
