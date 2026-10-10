package com.ecommerce.agent.copyseo;

import java.util.Map;

/** 脱敏后的工具调用摘要，不保存凭据或完整上游响应。 */
public record ToolCallTrace(
        String tool,
        ToolCallStatus status,
        long durationMs,
        Map<String, Object> summary,
        String errorType) {
}
