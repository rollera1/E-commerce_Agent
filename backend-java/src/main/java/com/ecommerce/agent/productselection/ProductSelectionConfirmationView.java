package com.ecommerce.agent.productselection;

/** 商家完成选品确认后的状态。 */
public record ProductSelectionConfirmationView(
        String selectionId,
        ProductSelectionStatus status,
        String selectedSourceProductId) {
}
