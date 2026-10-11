package com.ecommerce.agent.integration.python;

import com.ecommerce.agent.copyseo.AgentGeneration;
import com.ecommerce.agent.copyseo.AgentRunFailure;
import com.ecommerce.agent.copyseo.AgentRunResult;
import com.ecommerce.agent.copyseo.AgentRunStatus;
import com.ecommerce.agent.copyseo.CopySeoAgentRunGateway;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 通过 Python Agent Runtime API 管理异步 Copy/SEO 任务。 */
@Component
public class PythonCopySeoAgentRunClient implements CopySeoAgentRunGateway {
    private final RestClient restClient;

    @Autowired
    public PythonCopySeoAgentRunClient(PythonServiceProperties properties) {
        this(RestClient.builder().baseUrl(properties.baseUrl().toString()).build());
    }

    PythonCopySeoAgentRunClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AgentRunResult submit(
            String generationId,
            int attempt,
            String sourceProductId,
            String targetMarket,
            String language) {
        return Objects.requireNonNull(restClient.post()
                .uri("/internal/agent-runs")
                .body(Map.of(
                        "idempotencyKey", "copy-seo:" + generationId + ":" + attempt,
                        "agentType", "COPY_SEO",
                        "businessObjectType", "COPY_SEO_GENERATION",
                        "businessObjectId", generationId,
                        "input", Map.of(
                                "sourceProductId", sourceProductId,
                                "targetMarket", targetMarket,
                                "language", language)))
                .retrieve()
                .body(PythonAgentRunResponse.class), "Python Agent Run 提交响应不能为空")
                .toResult();
    }

    @Override
    public AgentRunResult get(String runId) {
        return Objects.requireNonNull(restClient.get()
                .uri("/internal/agent-runs/{runId}", runId)
                .retrieve()
                .body(PythonAgentRunResponse.class), "Python Agent Run 查询响应不能为空")
                .toResult();
    }

    @Override
    public AgentRunResult cancel(String runId) {
        return Objects.requireNonNull(restClient.post()
                .uri("/internal/agent-runs/{runId}/cancel", runId)
                .retrieve()
                .body(PythonAgentRunResponse.class), "Python Agent Run 取消响应不能为空")
                .toResult();
    }

    private record PythonAgentRunResponse(
            String runId,
            AgentRunStatus status,
            AgentGeneration output,
            PythonAgentRunError error) {
        AgentRunResult toResult() {
            AgentRunFailure failure = error == null
                    ? null : new AgentRunFailure(error.errorType(), error.retryable());
            return new AgentRunResult(runId, status, output, failure);
        }
    }

    private record PythonAgentRunError(String errorType, boolean retryable) {
    }
}
