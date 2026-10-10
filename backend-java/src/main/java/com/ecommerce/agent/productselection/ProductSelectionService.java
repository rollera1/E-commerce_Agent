package com.ecommerce.agent.productselection;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 负责选品 Agent 调用、候选商品保存和商家确认。 */
@Service
public class ProductSelectionService {
    private final ProductSelectionAgentGateway agentGateway;
    private final ProductSelectionRepository repository;
    private final Clock clock;

    @Autowired
    public ProductSelectionService(
            ProductSelectionAgentGateway agentGateway,
            ProductSelectionRepository repository) {
        this(agentGateway, repository, Clock.systemUTC());
    }

    ProductSelectionService(
            ProductSelectionAgentGateway agentGateway,
            ProductSelectionRepository repository,
            Clock clock) {
        this.agentGateway = agentGateway;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ProductSelectionSearchView search(ProductSelectionSearchRequest request) {
        AgentProductSelection result = agentGateway.search(request.query(), request.resolvedLimit());
        ProductSelection selection = ProductSelection.awaitingConfirmation(
                result, clock.instant());
        repository.save(selection);
        return new ProductSelectionSearchView(
                result.selectionId(),
                result.query(),
                result.matchedQuery(),
                result.outcome(),
                ProductSelectionStatus.AWAITING_CONFIRMATION,
                result.candidates(),
                result.trace());
    }

    @Transactional
    public ProductSelectionConfirmationView confirm(
            String selectionId,
            String sourceProductId) {
        ProductSelection selection = repository.findForConfirmation(selectionId)
                .orElseThrow(() -> new ProductSelectionNotFoundException(selectionId));
        ProductSelection task = selection.confirmedTask(sourceProductId, clock.instant());
        return repository.findBySearchIdAndSelectedSourceProductId(selection.originSearchId(), sourceProductId)
                .orElseGet(() -> repository.save(task)).toConfirmationView();
    }

    @Transactional(readOnly = true)
    public String requireConfirmedSourceProduct(String selectionId) {
        return find(selectionId).requireConfirmedSourceProductId();
    }

    private ProductSelection find(String selectionId) {
        return repository.findById(selectionId)
                .orElseThrow(() -> new ProductSelectionNotFoundException(selectionId));
    }
}
