package com.ecommerce.agent.copyseo;

/** 调用文案与 SEO Agent 失败时抛出的异常。 */
public class CopySeoAgentException extends RuntimeException {
    // 供数据库和前端识别的错误类型。
    private final String errorType;
    // 表示本次错误是否适合稍后重试。
    private final boolean retryable;

    public CopySeoAgentException(String errorType, boolean retryable, Throwable cause) {
        super("文案与 SEO Agent 执行失败：" + errorType, cause);
        this.errorType = errorType;
        this.retryable = retryable;
    }

    public String errorType() {
        return errorType;
    }

    public boolean retryable() {
        return retryable;
    }
}
