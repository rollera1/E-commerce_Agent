package com.ecommerce.agent.copyseo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class CopySeoReviewTest {
    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"ACCEPTED", "REJECTED"})
    void repeatedReviewPreservesStatusContentAndTimestamp(ReviewStatus decision) {
        var generation = pending();
        Instant reviewedAt = Instant.EPOCH.plusSeconds(10);
        review(generation, decision, reviewedAt);
        var reviewed = generation.toView();
        review(generation, decision, reviewedAt.plusSeconds(20));
        assertThat(generation.toView()).isEqualTo(reviewed);
        assertThat(ReflectionTestUtils.getField(generation, "updatedAt")).isEqualTo(reviewedAt);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"ACCEPTED", "REJECTED"})
    void oppositeReviewRemainsAConflictWithoutChangingTimestamp(ReviewStatus decision) {
        var generation = pending();
        review(generation, decision == ReviewStatus.ACCEPTED ? ReviewStatus.REJECTED : ReviewStatus.ACCEPTED,
                Instant.EPOCH);
        var before = generation.toView();
        assertThatThrownBy(() -> review(generation, decision, Instant.EPOCH.plusSeconds(10)))
                .isInstanceOf(CopySeoReviewConflictException.class).hasMessage(conflictMessage(decision));
        assertThat(generation.toView()).isEqualTo(before);
        assertThat(ReflectionTestUtils.getField(generation, "updatedAt")).isEqualTo(Instant.EPOCH);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"ACCEPTED", "REJECTED"})
    void failedGenerationCannotBeReviewed(ReviewStatus decision) {
        var generation = CopySeoGeneration.failed("source-1", "US", "en",
                new CopySeoAgentException("TIMEOUT", true, null), 10, Instant.EPOCH);
        assertThatThrownBy(() -> review(generation, decision, Instant.EPOCH.plusSeconds(10)))
                .isInstanceOf(CopySeoReviewConflictException.class).hasMessage(conflictMessage(decision));
        assertThat(generation.toView().reviewStatus()).isEqualTo(ReviewStatus.NOT_APPLICABLE);
        assertThat(ReflectionTestUtils.getField(generation, "updatedAt")).isEqualTo(Instant.EPOCH);
    }

    private static String conflictMessage(ReviewStatus decision) {
        return decision == ReviewStatus.ACCEPTED
                ? "已拒绝或不可审核的文案与 SEO 内容不能接受"
                : "已接受或不可审核的文案与 SEO 内容不能拒绝";
    }

    private static void review(CopySeoGeneration generation, ReviewStatus decision, Instant now) {
        if (decision == ReviewStatus.ACCEPTED) generation.accept(now);
        else generation.reject(now);
    }

    private static CopySeoGeneration pending() {
        return CopySeoGeneration.pendingReview(new AgentGeneration(
                "generation-1", "source-1", GenerationStatus.SUCCEEDED, ReviewStatus.PENDING_REVIEW,
                new CopySeoContent("Lamp", List.of("Light"), "Desk lamp", "Lamp", "Desk lamp", List.of("lamp")),
                new AgentExecution("COPY_SEO", "fake", "v1", "fake", 0), null),
                "US", "en", Instant.EPOCH);
    }
}
