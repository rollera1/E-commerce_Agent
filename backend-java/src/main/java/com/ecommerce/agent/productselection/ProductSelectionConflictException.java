package com.ecommerce.agent.productselection;

/** 选品确认或下游操作违反当前业务状态。 */
public class ProductSelectionConflictException extends RuntimeException {
    public ProductSelectionConflictException(String message) {
        super(message);
    }
}
