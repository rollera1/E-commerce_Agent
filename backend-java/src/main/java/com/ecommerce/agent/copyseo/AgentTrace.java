package com.ecommerce.agent.copyseo;

import java.util.List;

/** 可持久化、可展示的 Agent 执行轨迹；不包含模型隐藏推理。 */
public record AgentTrace(
        String currentStep,
        AgentOutcome outcome,
        List<String> steps,
        List<ToolCallTrace> toolCalls,
        int modelAttempts,
        int revisionCount,
        List<String> validationIssues,
        String nextAction) {
}
