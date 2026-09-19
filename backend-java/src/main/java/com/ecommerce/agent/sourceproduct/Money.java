package com.ecommerce.agent.sourceproduct;

import java.math.BigDecimal;

/** 商品金额，包含数值和三位币种代码。
 * amount 金额数值
 * currency 三位币种代码
 * */
public record Money(BigDecimal amount, String currency) {}
