package com.ecommerce.agent.productselection;

/** 请求的选品记录不存在。 */
public class ProductSelectionNotFoundException extends RuntimeException {
    public ProductSelectionNotFoundException(String selectionId) {
        super("选品记录不存在：" + selectionId);
    }
}
