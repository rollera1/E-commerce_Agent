package com.ecommerce.agent.copyseo;

/** 返回给前端的文案生成记录。 */
public record CopySeoGenerationView(
        // 生成记录 ID。
        String generationId,
        // 来源商品 ID。
        String sourceProductId,
        // 目标市场代码。
        String targetMarket,
        // 目标语言代码。
        String language,
        // Agent 执行状态。
        GenerationStatus status,
        // 商家审核状态。
        ReviewStatus reviewStatus,
        // 生成内容；失败记录中为空。
        CopySeoContent content,
        // Agent 执行信息。
        AgentExecution execution,
        // Agent 工具和编排轨迹；旧记录可能为空。
        AgentTrace trace,
        // 失败详情；成功记录中为空。
        AgentFailure failure) {
}
