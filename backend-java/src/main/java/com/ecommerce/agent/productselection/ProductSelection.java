package com.ecommerce.agent.productselection;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 需要商家明确确认的选品记录。 */
@Entity
@Table(name = "product_selection")
public class ProductSelection {
    @Id
    @Column(name = "selection_id", nullable = false, length = 36)
    private String selectionId;

    @Column(name = "query_text", nullable = false, length = 100)
    private String query;

    @Column(name = "matched_query", nullable = false, length = 100)
    private String matchedQuery;

    @Convert(converter = CandidateIdListConverter.class)
    @Column(name = "candidate_product_ids", nullable = false, columnDefinition = "TEXT")
    private List<String> candidateProductIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProductSelectionStatus status;

    @Column(name = "selected_source_product_id", length = 200)
    private String selectedSourceProductId;

    @Column(name = "search_id", length = 36)
    private String searchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected ProductSelection() {
    }

    public static ProductSelection awaitingConfirmation(
            AgentProductSelection result,
            Instant now) {
        ProductSelection selection = new ProductSelection();
        selection.selectionId = result.selectionId();
        selection.query = result.query();
        selection.matchedQuery = result.matchedQuery();
        selection.candidateProductIds = result.candidates().stream()
                .map(SelectionCandidate::sourceProductId)
                .toList();
        selection.status = ProductSelectionStatus.AWAITING_CONFIRMATION;
        selection.createdAt = now;
        return selection;
    }

    public ProductSelection confirmedTask(String sourceProductId, Instant now) {
        if (!candidateProductIds.contains(sourceProductId)) {
            throw new ProductSelectionConflictException("只能确认选品 Agent 返回的候选商品");
        }
        // 兼容旧的已确认记录；始终保留它与既有文案、草稿的关联。
        if (status == ProductSelectionStatus.CONFIRMED && sourceProductId.equals(selectedSourceProductId)) {
            return this;
        }
        ProductSelection task = new ProductSelection();
        task.selectionId = UUID.randomUUID().toString();
        task.searchId = searchId == null ? selectionId : searchId;
        task.query = query;
        task.matchedQuery = matchedQuery;
        task.candidateProductIds = List.copyOf(candidateProductIds);
        task.status = ProductSelectionStatus.CONFIRMED;
        task.selectedSourceProductId = sourceProductId;
        task.createdAt = now;
        task.confirmedAt = now;
        return task;
    }

    public String originSearchId() {
        return searchId == null ? selectionId : searchId;
    }

    public String requireConfirmedSourceProductId() {
        if (status != ProductSelectionStatus.CONFIRMED || selectedSourceProductId == null) {
            throw new ProductSelectionConflictException("必须先由商家确认候选商品");
        }
        return selectedSourceProductId;
    }

    public ProductSelectionConfirmationView toConfirmationView() {
        return new ProductSelectionConfirmationView(selectionId, status, selectedSourceProductId);
    }
}
