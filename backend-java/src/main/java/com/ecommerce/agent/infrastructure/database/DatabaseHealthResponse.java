package com.ecommerce.agent.infrastructure.database;

/** 数据库健康检查结果。 */
public record DatabaseHealthResponse(
        // 检查状态。
        String status,
        // 被检查的数据库服务名称。
        String service,
        // SELECT 1 返回的探针值。
        int probe) {
}
