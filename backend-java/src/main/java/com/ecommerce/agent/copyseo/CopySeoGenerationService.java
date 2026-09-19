package com.ecommerce.agent.copyseo;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 负责文案生成、结果保存以及商家审核操作。 */
@Service
public class CopySeoGenerationService {
    // 文案 Agent 调用接口，当前由 Python 客户端实现。
    private final CopySeoAgentGateway agentGateway;
    // 文案生成记录的数据访问接口。
    private final CopySeoGenerationRepository repository;
    // 用于生成可测试的时间戳和计算执行耗时。
    private final Clock clock;

    @Autowired
    public CopySeoGenerationService(
            CopySeoAgentGateway agentGateway,
            CopySeoGenerationRepository repository) {
        this(agentGateway, repository, Clock.systemUTC());
    }

    CopySeoGenerationService(
            CopySeoAgentGateway agentGateway,
            CopySeoGenerationRepository repository,
            Clock clock) {
        // 这个构造器允许测试传入固定时钟。
        this.agentGateway = agentGateway;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public CopySeoGenerationView generate(CopySeoGenerationRequest request) {
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

    @Transactional
    public CopySeoGenerationView updateContent(String generationId, CopySeoContent content) {
        // 只有数据库中真实存在的生成记录才能被修改。
        CopySeoGeneration generation = repository.findById(generationId)
                .orElseThrow(() -> new CopySeoGenerationNotFoundException(generationId));
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

    private CopySeoGeneration find(String generationId) {
        return repository.findById(generationId)
                .orElseThrow(() -> new CopySeoGenerationNotFoundException(generationId));
    }
}
