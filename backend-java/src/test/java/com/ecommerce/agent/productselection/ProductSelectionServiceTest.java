package com.ecommerce.agent.productselection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.agent.sourceproduct.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSelectionServiceTest {

    @Mock
    private ProductSelectionAgentGateway agentGateway;

    @Mock
    private ProductSelectionRepository repository;

    @Test
    void persistsAgentCandidatesBeforeMerchantConfirmation() {
        when(agentGateway.search("宠物牵引绳", 10)).thenReturn(agentResult());
        when(repository.save(any(ProductSelection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = service();

        var result = service.search(new ProductSelectionSearchRequest("宠物牵引绳", 10));

        assertThat(result.status()).isEqualTo(ProductSelectionStatus.AWAITING_CONFIRMATION);
        assertThat(result.candidates()).extracting(SelectionCandidate::sourceProductId)
                .containsExactly("cj-1");
        verify(repository).save(any(ProductSelection.class));
    }

    @Test
    void usesTenCandidatesWhenLimitIsOmitted() {
        when(agentGateway.search("宠物牵引绳", 10)).thenReturn(agentResult());
        when(repository.save(any(ProductSelection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().search(new ProductSelectionSearchRequest("宠物牵引绳", null));

        verify(agentGateway).search("宠物牵引绳", 10);
    }

    @Test
    void confirmsOnlyACandidateReturnedByTheAgent() {
        var selection = ProductSelection.awaitingConfirmation(agentResult(), Instant.EPOCH);
        when(repository.findForConfirmation("selection-1")).thenReturn(Optional.of(selection));
        when(repository.save(any(ProductSelection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = service();

        var result = service.confirm("selection-1", "cj-1");

        assertThat(result.status()).isEqualTo(ProductSelectionStatus.CONFIRMED);
        assertThat(result.selectedSourceProductId()).isEqualTo("cj-1");
        assertThat(result.selectionId()).isNotEqualTo("selection-1");
        assertThat(selection.toConfirmationView().status()).isEqualTo(ProductSelectionStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void rejectsAProductThatWasNotReturnedByTheAgent() {
        var selection = ProductSelection.awaitingConfirmation(agentResult(), Instant.EPOCH);
        when(repository.findForConfirmation("selection-1")).thenReturn(Optional.of(selection));
        var service = service();

        assertThatThrownBy(() -> service.confirm("selection-1", "other-product"))
                .isInstanceOf(ProductSelectionConflictException.class);
    }

    @Test
    void copyGenerationCannotStartBeforeConfirmation() {
        var selection = ProductSelection.awaitingConfirmation(agentResult(), Instant.EPOCH);
        when(repository.findById("selection-1")).thenReturn(Optional.of(selection));

        assertThatThrownBy(() -> service().requireConfirmedSourceProduct("selection-1"))
                .isInstanceOf(ProductSelectionConflictException.class);
    }

    @Test
    void confirmsDifferentProductsAsIndependentTasksAndRepeatsIdempotently() {
        var original = agentResult();
        var firstCandidate = original.candidates().getFirst();
        var secondCandidate = new SelectionCandidate("cj-2", "CJ", "Another leash", "A leash",
                firstCandidate.images(), firstCandidate.price(), List.of(), "IN_STOCK",
                firstCandidate.sourceUrl(), firstCandidate.fetchedAt(), "pet leash");
        var search = ProductSelection.awaitingConfirmation(new AgentProductSelection(
                original.selectionId(), original.query(), original.matchedQuery(), original.outcome(),
                List.of(firstCandidate, secondCandidate), original.trace()), Instant.EPOCH);
        when(repository.findForConfirmation("selection-1")).thenReturn(Optional.of(search));
        var saved = new java.util.HashMap<String, ProductSelection>();
        when(repository.save(any(ProductSelection.class))).thenAnswer(invocation -> {
            ProductSelection task = invocation.getArgument(0);
            saved.put(task.requireConfirmedSourceProductId(), task);
            return task;
        });
        when(repository.findBySearchIdAndSelectedSourceProductId(any(), any())).thenAnswer(invocation ->
                Optional.ofNullable(saved.get(invocation.getArgument(1))));
        var service = service();
        var first = service.confirm("selection-1", "cj-1");
        var second = service.confirm("selection-1", "cj-2");
        assertThat(first.selectionId()).isNotEqualTo(second.selectionId());
        assertThat(service.confirm("selection-1", "cj-1")).isEqualTo(first);
        assertThat(saved).hasSize(2);
        assertThat(saved.get("cj-1").requireConfirmedSourceProductId()).isEqualTo("cj-1");
        assertThat(search.toConfirmationView().status()).isEqualTo(ProductSelectionStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void preservesLegacyConfirmedTaskWhenSelectingAnotherCandidate() {
        var first = ProductSelection.awaitingConfirmation(agentResult(), Instant.EPOCH)
                .confirmedTask("cj-1", Instant.EPOCH);
        assertThat(first.confirmedTask("cj-1", Instant.now())).isSameAs(first);
        assertThat(first.requireConfirmedSourceProductId()).isEqualTo("cj-1");
    }

    private ProductSelectionService service() {
        return new ProductSelectionService(
                agentGateway,
                repository,
                Clock.fixed(Instant.parse("2026-09-28T08:00:00Z"), ZoneOffset.UTC));
    }

    private AgentProductSelection agentResult() {
        return new AgentProductSelection(
                "selection-1",
                "宠物牵引绳",
                "pet leash",
                "CANDIDATES_FOUND",
                List.of(new SelectionCandidate(
                        "cj-1",
                        "CJ",
                        "Pet Leash",
                        "A leash",
                        List.of("https://example.invalid/leash.jpg"),
                        new Money(new BigDecimal("9.99"), "USD"),
                        List.of(),
                        "IN_STOCK",
                        "https://example.invalid/cj-1",
                        "2026-09-28T08:00:00Z",
                        "pet leash")),
                Map.of("searchCount", 1));
    }
}
