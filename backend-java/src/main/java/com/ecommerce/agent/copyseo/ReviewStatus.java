package com.ecommerce.agent.copyseo;

/** 文案生成结果的人工审核状态。 */
public enum ReviewStatus {
    // 等待商家检查和修改。
    PENDING_REVIEW,
    // 商家已接受。
    ACCEPTED,
    // 商家已拒绝。
    REJECTED,
    // Agent 执行失败，没有内容可供审核。
    NOT_APPLICABLE
}
