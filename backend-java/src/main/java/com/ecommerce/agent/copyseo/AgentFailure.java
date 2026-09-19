package com.ecommerce.agent.copyseo;

/** Agent 执行失败信息。 */
public record AgentFailure(
        // 错误类型，例如 RATE_LIMITED 或 TIMEOUT。
        String errorType,
        // 是否适合稍后重新执行。
        boolean retryable) {
}
