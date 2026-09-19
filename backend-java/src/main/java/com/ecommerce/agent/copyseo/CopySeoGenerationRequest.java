package com.ecommerce.agent.copyseo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 发起一次文案生成所需的业务参数。 */
public record CopySeoGenerationRequest(
        // 来源平台中的商品 ID。
        @NotBlank(message = "来源商品 ID 不能为空")
        @Size(max = 200, message = "来源商品 ID 不能超过 200 个字符") String sourceProductId,
        // 两位大写目标市场代码，例如 US。
        @Pattern(regexp = "^[A-Z]{2}$", message = "目标市场必须是两位大写代码，例如 US") String targetMarket,
        // 两位小写语言代码，例如 en。
        @Pattern(regexp = "^[a-z]{2}$", message = "目标语言必须是两位小写代码，例如 en") String language) {
}
