package com.ecommerce.agent.copyseo;

/** Python 文案 Agent 返回的一次生成结果。 */
public record AgentGeneration(
        // 本次生成记录的唯一 ID。
        String generationId,
        // 生成内容所对应的来源商品 ID。
        String sourceProductId,
        // Agent 执行状态。
        GenerationStatus status,
        // 当前人工审核状态。
        ReviewStatus reviewStatus,
        // Agent 生成的文案；执行失败时为空。
        CopySeoContent content,
        // 模型、提示词版本和耗时等执行信息。
        AgentExecution execution,
        // 工具、步骤、校验和回退等脱敏轨迹。
        AgentTrace trace) {
}
