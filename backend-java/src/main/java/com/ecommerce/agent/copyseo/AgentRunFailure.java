package com.ecommerce.agent.copyseo;

/** Agent Run 的脱敏失败信息。 */
public record AgentRunFailure(String errorType, boolean retryable) {
}
