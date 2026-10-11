package com.ecommerce.agent.copyseo;

/** Agent 的执行状态。 */
public enum GenerationStatus {
    // Java 已持久化任务，等待 Python Worker 领取。
    SUBMITTED,
    // Python Worker 正在执行任务。
    RUNNING,
    // Agent 已正常返回符合结构要求的内容。
    SUCCEEDED,
    // Agent 调用或输出校验失败。
    FAILED,
    // 用户已取消任务。
    CANCELLED
}
