package com.ecommerce.agent.copyseo;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 负责文案生成、结果保存以及商家审核操作。 */
@Service
public class CopySeoGenerationService {
    // 文案 Agent 调用接口，当前由 Python 客户端实现。
    private final CopySeoAgentGateway agentGateway;
    // 异步模式下由 Java 控制 Python Agent Run。
    private final CopySeoAgentRunGateway agentRunGateway;
    // 文案生成记录的数据访问接口。
    private final CopySeoGenerationRepository repository;
    // 用于生成可测试的时间戳和计算执行耗时。
    private final Clock clock;
    private final boolean asyncExecution;
    private final Duration pollDelay;

    @Autowired
    public CopySeoGenerationService(
            CopySeoAgentGateway agentGateway,
            CopySeoAgentRunGateway agentRunGateway,
            CopySeoGenerationRepository repository,
            @Value("${agent.execution-mode:sync}") String executionMode,
            @Value("${agent.poll-delay-ms:2000}") long pollDelayMs) {
        this(agentGateway, agentRunGateway, repository, Clock.systemUTC(),
                "async".equalsIgnoreCase(executionMode), Duration.ofMillis(pollDelayMs));
    }

    CopySeoGenerationService(
            CopySeoAgentGateway agentGateway,
            CopySeoGenerationRepository repository) {
        this(agentGateway, repository, Clock.systemUTC());
    }

    CopySeoGenerationService(
            CopySeoAgentGateway agentGateway,
            CopySeoGenerationRepository repository,
            Clock clock) {
        this(agentGateway, null, repository, clock, false, Duration.ofSeconds(2));
    }

    CopySeoGenerationService(
            CopySeoAgentGateway agentGateway,
            CopySeoAgentRunGateway agentRunGateway,
            CopySeoGenerationRepository repository,
            Clock clock,
            boolean asyncExecution,
            Duration pollDelay) {
        // 这个构造器允许测试传入固定时钟。
        this.agentGateway = agentGateway;
        this.agentRunGateway = agentRunGateway;
        this.repository = repository;
        this.clock = clock;
        this.asyncExecution = asyncExecution;
        this.pollDelay = pollDelay;
    }

    @Transactional
    public CopySeoGenerationView generate(CopySeoGenerationRequest request) {
        if (asyncExecution) return submit(request);
        long startedAt = clock.millis();
        try {
            // 调用 Python 服务中的文案与 SEO Agent。
            AgentGeneration generated = agentGateway.generate(
                    request.sourceProductId(), request.targetMarket(), request.language());
            CopySeoGeneration generation = CopySeoGeneration.pendingReview(
                    generated, request.targetMarket(), request.language(), clock.instant());
            return repository.save(generation).toView();
        } catch (CopySeoAgentException failure) {
            // Agent 失败也保存记录，方便页面展示、排查和后续重试。
            long durationMs = Math.max(0, clock.millis() - startedAt);
            CopySeoGeneration generation = CopySeoGeneration.failed(
                    request.sourceProductId(), request.targetMarket(), request.language(),
                    failure, durationMs, clock.instant());
            return repository.save(generation).toView();
        }
    }

    private CopySeoGenerationView submit(CopySeoGenerationRequest request) {
        CopySeoGeneration generation = repository.save(
                CopySeoGeneration.submitted(request, clock.instant()));
        try {
            submitNextRun(generation);
        } catch (RuntimeException ignored) {
            // 提交失败时保留 MySQL 任务，轮询器会用同一个幂等键重提。
        }
        return repository.save(generation).toView();
    }

    @Transactional(readOnly = true)
    public List<String> pendingGenerationIds() {
        return repository
                .findTop20ByStatusInAndNextPollAtLessThanEqualOrderByCreatedAtAsc(
                        EnumSet.of(GenerationStatus.SUBMITTED, GenerationStatus.RUNNING),
                        clock.instant())
                .stream()
                .map(CopySeoGeneration::generationId)
                .toList();
    }

    @Transactional
    public CopySeoGenerationView poll(String generationId) {
        CopySeoGeneration generation = find(generationId);
        if (!generation.awaitingAgent()) return generation.toView();
        if (generation.agentRunId() == null) {
            submitNextRun(generation);
            return repository.save(generation).toView();
        }

        AgentRunResult run = agentRunGateway.get(generation.agentRunId());
        if (run == null || run.status() == null) {
            generation.fail(new AgentRunFailure("INVALID_AGENT_RUN_RESPONSE", false), clock.instant());
        } else if (run.status() == AgentRunStatus.SUCCEEDED) {
            if (!validOutput(generation, run.output())) {
                generation.fail(new AgentRunFailure("INVALID_AGENT_RUN_OUTPUT", false), clock.instant());
            } else {
                generation.complete(run.output(), clock.instant());
            }
        } else if (run.status() == AgentRunStatus.FAILED) {
            if (run.failure() != null && run.failure().retryable()
                    && generation.agentRunAttempt() < 2) {
                generation.prepareRetry(clock.instant());
                submitNextRun(generation);
            } else {
                generation.fail(run.failure(), clock.instant());
            }
        } else if (run.status() == AgentRunStatus.CANCELLED) {
            generation.cancel(clock.instant());
        } else {
            generation.observe(run.status(), nextPollAt(), clock.instant());
        }
        return repository.save(generation).toView();
    }

    @Transactional
    public CopySeoGenerationView cancel(String generationId) {
        CopySeoGeneration generation = find(generationId);
        if (!generation.awaitingAgent()) return generation.toView();
        if (generation.agentRunId() != null) {
            agentRunGateway.cancel(generation.agentRunId());
        }
        generation.cancel(clock.instant());
        return repository.save(generation).toView();
    }

    private void submitNextRun(CopySeoGeneration generation) {
        int attempt = generation.agentRunAttempt() + 1;
        AgentRunResult run = agentRunGateway.submit(
                generation.generationId(),
                attempt,
                generation.sourceProductId(),
                generation.targetMarket(),
                generation.language());
        generation.bindRun(run, attempt, nextPollAt(), clock.instant());
    }

    private java.time.Instant nextPollAt() {
        return clock.instant().plus(pollDelay);
    }

    private boolean validOutput(CopySeoGeneration generation, AgentGeneration output) {
        return output != null
                && output.status() == GenerationStatus.SUCCEEDED
                && generation.sourceProductId().equals(output.sourceProductId())
                && output.content() != null
                && output.execution() != null;
    }

    @Transactional
    public CopySeoGenerationView updateContent(String generationId, CopySeoContent content) {
        // 只有数据库中真实存在的生成记录才能被修改。
        CopySeoGeneration generation = find(generationId);
        generation.updateContent(content, clock.instant());
        return repository.save(generation).toView();
    }

    @Transactional
    public CopySeoGenerationView accept(String generationId) {
        CopySeoGeneration generation = find(generationId);
        generation.accept(clock.instant());
        return repository.save(generation).toView();
    }

    @Transactional
    public CopySeoGenerationView reject(String generationId) {
        CopySeoGeneration generation = find(generationId);
        generation.reject(clock.instant());
        return repository.save(generation).toView();
    }

    @Transactional
    public CopySeoGenerationView reopen(String generationId) {
        CopySeoGeneration generation = find(generationId);
        generation.reopen(clock.instant());
        return repository.save(generation).toView();
    }

    @Transactional(readOnly = true)
    public CopySeoGenerationView get(String generationId) {
        return find(generationId).toView();
    }

    private CopySeoGeneration find(String generationId) {
        return repository.findById(generationId)
                .orElseThrow(() -> new CopySeoGenerationNotFoundException(generationId));
    }
}
