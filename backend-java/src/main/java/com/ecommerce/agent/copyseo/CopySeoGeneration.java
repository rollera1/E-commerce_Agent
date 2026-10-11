package com.ecommerce.agent.copyseo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 文案与 SEO 生成记录实体，对应数据库表 copy_seo_generation。 */
@Entity
@Table(name = "copy_seo_generation")
public class CopySeoGeneration {
    // 本次生成记录的唯一 ID。
    @Id
    @Column(name = "generation_id", nullable = false, length = 36)
    private String generationId;

    // 来源平台中的商品 ID。
    @Column(name = "source_product_id", nullable = false, length = 200)
    private String sourceProductId;

    // 两位目标市场代码，例如 US。
    @Column(name = "target_market", nullable = false, length = 2)
    private String targetMarket;

    // 两位目标语言代码，例如 en。
    @Column(name = "language", nullable = false, length = 2)
    private String language;

    // Agent 执行是否成功。
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GenerationStatus status;

    // 商家对生成内容的审核状态。
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private ReviewStatus reviewStatus;

    // Agent 生成或商家修改后的商品标题。
    @Column(name = "title")
    private String title;

    // 商品卖点列表，经转换器保存到 TEXT 字段。
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "bullet_points", columnDefinition = "TEXT")
    private List<String> bulletPoints;

    // 商品详细描述。
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // 搜索引擎结果标题。
    @Column(name = "seo_title", length = 70)
    private String seoTitle;

    // 搜索引擎结果摘要。
    @Column(name = "meta_description", length = 160)
    private String metaDescription;

    // 商品和 SEO 标签列表。
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "tags", columnDefinition = "TEXT")
    private List<String> tags;

    // 执行本次任务的 Agent 名称。
    @Column(name = "agent", nullable = false, length = 64)
    private String agent;
    // Agent 实现版本。
    @Column(name = "agent_version", nullable = false, length = 64)
    private String agentVersion;
    // 提示词版本。
    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;
    // 实际使用的模型名称。
    @Column(name = "model", nullable = false, length = 100)
    private String model;
    // Agent 执行耗时，单位为毫秒。
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
    // 失败类型；成功记录为空。
    @Column(name = "error_type", length = 64)
    private String errorType;
    // 失败后是否适合重试；成功记录为空。
    @Column(name = "retryable")
    private Boolean retryable;
    // Agent 工具调用、步骤和回退轨迹，以 JSON 保存到 TEXT。
    @Convert(converter = AgentTraceJsonConverter.class)
    @Column(name = "agent_trace", columnDefinition = "TEXT")
    private AgentTrace trace;
    // 记录创建时间。
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    // 最近一次编辑或审核时间。
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Python Agent Runtime 返回的运行 ID。
    @Column(name = "agent_run_id", length = 36)
    private String agentRunId;
    // Java 已提交的整次 Agent Run 次数，最多两次。
    @Column(name = "agent_run_attempt", nullable = false)
    private int agentRunAttempt;
    // Java 下一次允许查询或重提任务的时间。
    @Column(name = "next_poll_at")
    private Instant nextPollAt;
    // 防止多实例同时推进同一业务任务时互相覆盖。
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CopySeoGeneration() {
        // JPA 加载实体时使用。
    }

    private CopySeoGeneration(AgentGeneration generated, String targetMarket, String language, Instant now) {
        this.generationId = generated.generationId();
        this.sourceProductId = generated.sourceProductId();
        this.targetMarket = targetMarket;
        this.language = language;
        this.status = generated.status();
        this.reviewStatus = ReviewStatus.PENDING_REVIEW;
        applyContent(generated.content());
        this.agent = generated.execution().agent();
        this.agentVersion = generated.execution().agentVersion();
        this.promptVersion = generated.execution().promptVersion();
        this.model = generated.execution().model();
        this.durationMs = generated.execution().durationMs();
        this.trace = generated.trace();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CopySeoGeneration pendingReview(
            AgentGeneration generated, String targetMarket, String language, Instant now) {
        return new CopySeoGeneration(generated, targetMarket, language, now);
    }

    public static CopySeoGeneration submitted(CopySeoGenerationRequest request, Instant now) {
        CopySeoGeneration generation = new CopySeoGeneration();
        generation.generationId = UUID.randomUUID().toString();
        generation.sourceProductId = request.sourceProductId();
        generation.targetMarket = request.targetMarket();
        generation.language = request.language();
        generation.status = GenerationStatus.SUBMITTED;
        generation.reviewStatus = ReviewStatus.NOT_APPLICABLE;
        generation.agent = "COPY_SEO";
        generation.agentVersion = "pending";
        generation.promptVersion = "pending";
        generation.model = "pending";
        generation.createdAt = now;
        generation.updatedAt = now;
        generation.nextPollAt = now;
        return generation;
    }

    public static CopySeoGeneration failed(
            String sourceProductId,
            String targetMarket,
            String language,
            CopySeoAgentException failure,
            long durationMs,
            Instant now) {
        // Python 没有返回 generationId 时，由 Java 为失败记录生成唯一 ID。
        CopySeoGeneration generation = new CopySeoGeneration();
        generation.generationId = UUID.randomUUID().toString();
        generation.sourceProductId = sourceProductId;
        generation.targetMarket = targetMarket;
        generation.language = language;
        generation.status = GenerationStatus.FAILED;
        generation.reviewStatus = ReviewStatus.NOT_APPLICABLE;
        generation.agent = "COPY_SEO";
        generation.agentVersion = "python-agent";
        generation.promptVersion = "unknown";
        generation.model = "unknown";
        generation.durationMs = durationMs;
        generation.errorType = failure.errorType();
        generation.retryable = failure.retryable();
        generation.trace = failure.trace();
        generation.createdAt = now;
        generation.updatedAt = now;
        return generation;
    }

    public void bindRun(AgentRunResult run, int attempt, Instant nextPollAt, Instant now) {
        this.agentRunId = run.runId();
        this.agentRunAttempt = attempt;
        updateRunStatus(run.status(), nextPollAt, now);
    }

    public void observe(AgentRunStatus runStatus, Instant nextPollAt, Instant now) {
        updateRunStatus(runStatus, nextPollAt, now);
    }

    public void complete(AgentGeneration generated, Instant now) {
        this.status = GenerationStatus.SUCCEEDED;
        this.reviewStatus = ReviewStatus.PENDING_REVIEW;
        applyContent(generated.content());
        this.agent = generated.execution().agent();
        this.agentVersion = generated.execution().agentVersion();
        this.promptVersion = generated.execution().promptVersion();
        this.model = generated.execution().model();
        this.durationMs = generated.execution().durationMs();
        this.trace = generated.trace();
        this.errorType = null;
        this.retryable = null;
        this.nextPollAt = null;
        this.updatedAt = now;
    }

    public void prepareRetry(Instant now) {
        this.agentRunId = null;
        this.status = GenerationStatus.SUBMITTED;
        this.nextPollAt = now;
        this.updatedAt = now;
    }

    public void fail(AgentRunFailure failure, Instant now) {
        this.status = GenerationStatus.FAILED;
        this.reviewStatus = ReviewStatus.NOT_APPLICABLE;
        this.errorType = failure == null ? "AGENT_RUN_FAILED" : failure.errorType();
        this.retryable = failure != null && failure.retryable();
        this.nextPollAt = null;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = GenerationStatus.CANCELLED;
        this.reviewStatus = ReviewStatus.NOT_APPLICABLE;
        this.nextPollAt = null;
        this.updatedAt = now;
    }

    public boolean awaitingAgent() {
        return status == GenerationStatus.SUBMITTED || status == GenerationStatus.RUNNING;
    }

    private void updateRunStatus(AgentRunStatus runStatus, Instant pollAt, Instant now) {
        if (runStatus == AgentRunStatus.RUNNING) {
            this.status = GenerationStatus.RUNNING;
        } else if (runStatus == AgentRunStatus.SUBMITTED) {
            this.status = GenerationStatus.SUBMITTED;
        }
        this.nextPollAt = pollAt;
        this.updatedAt = now;
    }

    private void applyContent(CopySeoContent content) {
        // 列表重新复制，避免实体中的内容被外部对象继续修改。
        this.title = content.title();
        this.bulletPoints = List.copyOf(content.bulletPoints());
        this.description = content.description();
        this.seoTitle = content.seoTitle();
        this.metaDescription = content.metaDescription();
        this.tags = List.copyOf(content.tags());
    }

    public void updateContent(CopySeoContent content, Instant now) {
        if (reviewStatus != ReviewStatus.PENDING_REVIEW) {
            throw new CopySeoReviewConflictException(
                    "只有等待审核的文案与 SEO 内容可以修改");
        }
        applyContent(content);
        this.updatedAt = now;
    }

    public void accept(Instant now) {
        review(ReviewStatus.ACCEPTED, now, "已拒绝或不可审核的文案与 SEO 内容不能接受");
    }

    public void reject(Instant now) {
        review(ReviewStatus.REJECTED, now, "已接受或不可审核的文案与 SEO 内容不能拒绝");
    }

    private void review(ReviewStatus decision, Instant now, String conflictMessage) {
        // 重复审核不更新时间，保持原有幂等行为。
        if (reviewStatus == decision) return;
        if (reviewStatus != ReviewStatus.PENDING_REVIEW) {
            throw new CopySeoReviewConflictException(conflictMessage);
        }
        this.reviewStatus = decision;
        this.updatedAt = now;
    }

    public void reopen(Instant now) {
        if (reviewStatus != ReviewStatus.ACCEPTED) {
            throw new CopySeoReviewConflictException("只有已接受且未发布的内容可以退回编辑");
        }
        this.reviewStatus = ReviewStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    public CopySeoGenerationView toView() {
        // 失败记录没有可审核内容，成功记录也没有失败详情。
        CopySeoContent content = status == GenerationStatus.SUCCEEDED
                ? new CopySeoContent(title, bulletPoints, description, seoTitle, metaDescription, tags)
                : null;
        AgentFailure failure = status == GenerationStatus.FAILED
                ? new AgentFailure(errorType, Boolean.TRUE.equals(retryable))
                : null;
        return new CopySeoGenerationView(
                generationId, sourceProductId, targetMarket, language, status, reviewStatus,
                content,
                new AgentExecution(agent, agentVersion, promptVersion, model, durationMs),
                trace,
                failure);
    }

    public String generationId() { return generationId; }
    public String sourceProductId() { return sourceProductId; }
    public String targetMarket() { return targetMarket; }
    public String language() { return language; }
    public String agentRunId() { return agentRunId; }
    public int agentRunAttempt() { return agentRunAttempt; }
}
