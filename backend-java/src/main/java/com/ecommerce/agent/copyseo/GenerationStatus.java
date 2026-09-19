package com.ecommerce.agent.copyseo;

/** Agent 的执行状态。 */
public enum GenerationStatus {
    // Agent 已正常返回符合结构要求的内容。
    SUCCEEDED,
    // Agent 调用或输出校验失败。
    FAILED
}
