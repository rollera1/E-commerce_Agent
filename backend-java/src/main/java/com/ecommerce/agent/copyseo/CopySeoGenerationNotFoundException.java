package com.ecommerce.agent.copyseo;

/** 指定的文案生成记录不存在。 */
public class CopySeoGenerationNotFoundException extends RuntimeException {
    public CopySeoGenerationNotFoundException(String generationId) {
        super("未找到文案与 SEO 生成记录：" + generationId);
    }
}
