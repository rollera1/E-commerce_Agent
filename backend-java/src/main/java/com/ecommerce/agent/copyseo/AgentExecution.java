package com.ecommerce.agent.copyseo;

/** 一次 Agent 执   行的基本信息。 */
public record AgentExecution(
        // Agent 名称。
        String agent,
        // Agent 实现版本。
        String agentVersion,
        // 使用的提示词版本。
        String promptVersion,
        // 使用的模型名称。
        String model,
        // 本次执行耗时，单位为毫秒。
        long durationMs) {
}
