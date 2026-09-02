package com.ecommerce.agent.sourceproduct;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {}
