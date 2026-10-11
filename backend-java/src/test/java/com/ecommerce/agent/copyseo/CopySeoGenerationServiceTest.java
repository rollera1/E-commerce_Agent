package com.ecommerce.agent.copyseo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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

    @Mock
    private CopySeoAgentRunGateway agentRunGateway;

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
        assertThat(result.trace().outcome()).isEqualTo(AgentOutcome.COMPLETED);
        assertThat(result.trace().toolCalls()).hasSize(1);
        verify(repository).save(any(CopySeoGeneration.class));
    }

    @Test
    void persistsAgentFailureWithErrorTypeAndWithoutReviewableContent() {
        var failureTrace = new AgentTrace(
                "COMPLIANCE_REVIEW",
                AgentOutcome.NEEDS_HUMAN_INPUT,
                List.of("GENERATE_CONTENT", "CHECK_COMPLIANCE"),
                List.of(),
                2,
                1,
                List.of("UNSUPPORTED_CLAIM:lifetime warranty"),
                "HUMAN_CONTENT_REVIEW");
        when(agentGateway.generate("fake-desk-lamp-001", "US", "en"))
                .thenThrow(new CopySeoAgentException(
                        "CONTENT_REQUIRES_HUMAN_REVIEW", false, failureTrace, null));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CopySeoGenerationService(agentGateway, repository);

        var result = service.generate(new CopySeoGenerationRequest(
                "fake-desk-lamp-001", "US", "en"));

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NOT_APPLICABLE);
        assertThat(result.content()).isNull();
        assertThat(result.failure().errorType()).isEqualTo("CONTENT_REQUIRES_HUMAN_REVIEW");
        assertThat(result.failure().retryable()).isFalse();
        assertThat(result.trace()).isEqualTo(failureTrace);
        verify(repository).save(any(CopySeoGeneration.class));
    }

    @Test
    void asyncModeCommitsJavaTaskBeforeAnyPythonCall() {
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var service = asyncService();

        var result = service.generate(new CopySeoGenerationRequest(
                "fake-desk-lamp-001", "US", "en"));

        assertThat(result.status()).isEqualTo(GenerationStatus.SUBMITTED);
        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NOT_APPLICABLE);
        assertThat(result.content()).isNull();
        verifyNoInteractions(agentRunGateway);
    }

    @Test
    void pollLeavesCommittedMysqlTaskRetryableWhenPythonSubmissionIsUnavailable() {
        var persisted = new AtomicReference<CopySeoGeneration>();
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> {
                    CopySeoGeneration value = invocation.getArgument(0);
                    persisted.set(value);
                    return value;
                });
        var service = asyncService();
        var created = service.generate(new CopySeoGenerationRequest(
                "fake-desk-lamp-001", "US", "en"));
        CopySeoGeneration pending = persisted.get();
        when(repository.findById(created.generationId())).thenReturn(Optional.of(pending));
        when(agentRunGateway.submit(anyString(), eq(1), eq("fake-desk-lamp-001"), eq("US"), eq("en")))
                .thenThrow(new IllegalStateException("upstream response must not be persisted"));

        assertThatThrownBy(() -> service.poll(created.generationId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(pending.toView().status()).isEqualTo(GenerationStatus.SUBMITTED);
        assertThat(pending.agentRunAttempt()).isZero();
    }

    @Test
    void asyncPollPersistsSuccessfulStructuredOutput() {
        CopySeoGeneration pending = submittedWithRun("run-1", 1);
        when(repository.findById(pending.generationId())).thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunGateway.get("run-1"))
                .thenReturn(new AgentRunResult("run-1", AgentRunStatus.SUCCEEDED, generation(), null));

        var result = asyncService().poll(pending.generationId());

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCEEDED);
        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(result.content().title()).contains("Desk Lamp");
    }

    @Test
    void asyncPollRejectsOutputForAnotherSourceProduct() {
        CopySeoGeneration pending = submittedWithRun("run-1", 1);
        AgentGeneration mismatched = new AgentGeneration(
                "python-generation",
                "another-source",
                GenerationStatus.SUCCEEDED,
                ReviewStatus.PENDING_REVIEW,
                generation().content(),
                generation().execution(),
                generation().trace());
        when(repository.findById(pending.generationId())).thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunGateway.get("run-1"))
                .thenReturn(new AgentRunResult("run-1", AgentRunStatus.SUCCEEDED, mismatched, null));

        var result = asyncService().poll(pending.generationId());

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.failure().errorType()).isEqualTo("INVALID_AGENT_RUN_OUTPUT");
    }

    @Test
    void asyncPollRetriesOneWholeRunAndThenStops() {
        CopySeoGeneration pending = submittedWithRun("run-1", 1);
        when(repository.findById(pending.generationId())).thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunGateway.get("run-1"))
                .thenReturn(new AgentRunResult(
                        "run-1", AgentRunStatus.FAILED, null,
                        new AgentRunFailure("TIMEOUT", true)));
        when(agentRunGateway.submit(
                pending.generationId(), 2, "fake-desk-lamp-001", "US", "en"))
                .thenReturn(new AgentRunResult("run-2", AgentRunStatus.SUBMITTED, null, null));
        when(agentRunGateway.get("run-2"))
                .thenReturn(new AgentRunResult(
                        "run-2", AgentRunStatus.FAILED, null,
                        new AgentRunFailure("TIMEOUT", true)));
        var service = asyncService();

        var retried = service.poll(pending.generationId());
        var failed = service.poll(pending.generationId());

        assertThat(retried.status()).isEqualTo(GenerationStatus.SUBMITTED);
        assertThat(failed.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(failed.failure().errorType()).isEqualTo("TIMEOUT");
        verify(agentRunGateway).submit(
                pending.generationId(), 2, "fake-desk-lamp-001", "US", "en");
        verify(agentRunGateway, never()).submit(
                pending.generationId(), 3, "fake-desk-lamp-001", "US", "en");
    }

    @Test
    void cancelPropagatesToPythonAndTerminatesJavaTask() {
        CopySeoGeneration pending = submittedWithRun("run-1", 1);
        when(repository.findById(pending.generationId())).thenReturn(Optional.of(pending));
        when(repository.save(any(CopySeoGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunGateway.cancel("run-1"))
                .thenReturn(new AgentRunResult("run-1", AgentRunStatus.RUNNING, null, null));

        var result = asyncService().cancel(pending.generationId());

        assertThat(result.status()).isEqualTo(GenerationStatus.CANCELLED);
        verify(agentRunGateway).cancel("run-1");
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
                new AgentExecution("COPY_SEO", "copy-seo-agent-v2", "copy-seo-v1", "fake", 0),
                new AgentTrace(
                        "AWAIT_MERCHANT_REVIEW",
                        AgentOutcome.COMPLETED,
                        List.of("COLLECT_PRODUCT_CONTEXT", "AWAIT_MERCHANT_REVIEW"),
                        List.of(new ToolCallTrace(
                                "get_product_detail",
                                ToolCallStatus.SUCCEEDED,
                                0,
                                Map.of("variantCount", 1),
                                null)),
                        1,
                        0,
                        List.of(),
                        "MERCHANT_REVIEW"));
    }

    private CopySeoGenerationService asyncService() {
        return new CopySeoGenerationService(
                agentGateway,
                agentRunGateway,
                repository,
                Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC),
                true,
                Duration.ofSeconds(2));
    }

    private CopySeoGeneration submittedWithRun(String runId, int attempt) {
        Instant now = Instant.parse("2026-10-11T10:00:00Z");
        CopySeoGeneration generation = CopySeoGeneration.submitted(
                new CopySeoGenerationRequest("fake-desk-lamp-001", "US", "en"), now);
        generation.bindRun(
                new AgentRunResult(runId, AgentRunStatus.SUBMITTED, null, null),
                attempt,
                now,
                now);
        return generation;
    }
}
