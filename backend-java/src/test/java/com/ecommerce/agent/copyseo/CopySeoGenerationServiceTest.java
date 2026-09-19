package com.ecommerce.agent.copyseo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CopySeoGenerationServiceTest {

    @Mock
    private CopySeoAgentGateway agentGateway;

    @Mock
    private CopySeoGenerationRepository repository;

    @Test
    void persistsSuccessfulAgentOutputAsPendingMerchantReview() {
        var generated = generation();
        when(agentGateway.generate("fake-desk-lamp-001", "US", "en")).thenReturn(generated);
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CopySeoGenerationService(agentGateway, repository);

        var result = service.generate(new CopySeoGenerationRequest(
                "fake-desk-lamp-001", "US", "en"));

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCEEDED);
        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(result.content().title()).contains("Desk Lamp");
        verify(repository).save(any(CopySeoGeneration.class));
    }

    @Test
    void persistsAgentFailureWithErrorTypeAndWithoutReviewableContent() {
        when(agentGateway.generate("fake-desk-lamp-001", "US", "en"))
                .thenThrow(new CopySeoAgentException("RATE_LIMITED", true, null));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CopySeoGenerationService(agentGateway, repository);

        var result = service.generate(new CopySeoGenerationRequest(
                "fake-desk-lamp-001", "US", "en"));

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NOT_APPLICABLE);
        assertThat(result.content()).isNull();
        assertThat(result.failure().errorType()).isEqualTo("RATE_LIMITED");
        assertThat(result.failure().retryable()).isTrue();
        verify(repository).save(any(CopySeoGeneration.class));
    }

    @Test
    void merchantCanEditContentWhileGenerationIsPendingReview() {
        var pending = CopySeoGeneration.pendingReview(generation(), "US", "en", java.time.Instant.EPOCH);
        var edited = new CopySeoContent(
                "Merchant Edited Desk Lamp",
                List.of("Merchant-approved selling point"),
                "Merchant edited description.",
                "Merchant Edited Desk Lamp",
                "Merchant edited meta description.",
                List.of("edited"));
        when(repository.findById("9d1625ec-9c39-45c8-afc0-24e2e45b2158"))
                .thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CopySeoGenerationService(agentGateway, repository);

        var result = service.updateContent(
                "9d1625ec-9c39-45c8-afc0-24e2e45b2158", edited);

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(result.content()).isEqualTo(edited);
    }

    @Test
    void merchantCanAcceptPendingContent() {
        var pending = CopySeoGeneration.pendingReview(generation(), "US", "en", java.time.Instant.EPOCH);
        when(repository.findById("9d1625ec-9c39-45c8-afc0-24e2e45b2158"))
                .thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CopySeoGenerationService(agentGateway, repository);

        var result = service.accept("9d1625ec-9c39-45c8-afc0-24e2e45b2158");

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.ACCEPTED);
    }

    @Test
    void merchantCanRejectPendingContent() {
        var pending = CopySeoGeneration.pendingReview(generation(), "US", "en", java.time.Instant.EPOCH);
        when(repository.findById("9d1625ec-9c39-45c8-afc0-24e2e45b2158"))
                .thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CopySeoGenerationService(agentGateway, repository);

        var result = service.reject("9d1625ec-9c39-45c8-afc0-24e2e45b2158");

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    void acceptingTheSameGenerationTwiceIsIdempotent() {
        var pending = CopySeoGeneration.pendingReview(generation(), "US", "en", java.time.Instant.EPOCH);
        when(repository.findById("9d1625ec-9c39-45c8-afc0-24e2e45b2158"))
                .thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CopySeoGenerationService(agentGateway, repository);

        service.accept("9d1625ec-9c39-45c8-afc0-24e2e45b2158");
        var repeated = service.accept("9d1625ec-9c39-45c8-afc0-24e2e45b2158");

        assertThat(repeated.reviewStatus()).isEqualTo(ReviewStatus.ACCEPTED);
    }

    @Test
    void rejectingTheSameGenerationTwiceIsIdempotent() {
        var pending = CopySeoGeneration.pendingReview(generation(), "US", "en", java.time.Instant.EPOCH);
        when(repository.findById("9d1625ec-9c39-45c8-afc0-24e2e45b2158"))
                .thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CopySeoGenerationService(agentGateway, repository);

        service.reject("9d1625ec-9c39-45c8-afc0-24e2e45b2158");
        var repeated = service.reject("9d1625ec-9c39-45c8-afc0-24e2e45b2158");

        assertThat(repeated.reviewStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    private AgentGeneration generation() {
        return new AgentGeneration(
                "9d1625ec-9c39-45c8-afc0-24e2e45b2158",
                "fake-desk-lamp-001",
                GenerationStatus.SUCCEEDED,
                ReviewStatus.PENDING_REVIEW,
                new CopySeoContent(
                        "Adjustable LED Desk Lamp for Focused Workspaces",
                        List.of("Adjustable lighting", "Compact profile"),
                        "A generated product description.",
                        "Adjustable LED Desk Lamp | Home Office Lighting",
                        "A compact adjustable LED desk lamp for home offices.",
                        List.of("desk lamp", "home office")),
                new AgentExecution("COPY_SEO", "fake-v1", "copy-seo-v1", "fake", 0));
    }
}
