package com.ecommerce.agent.copyseo;

/** 当前审核状态不允许执行请求的操作。 */
public class CopySeoReviewConflictException extends RuntimeException {
    public CopySeoReviewConflictException(String message) {
        super(message);
    }
}
