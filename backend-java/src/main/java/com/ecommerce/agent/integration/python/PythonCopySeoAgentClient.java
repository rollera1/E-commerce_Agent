package com.ecommerce.agent.integration.python;

import com.ecommerce.agent.copyseo.AgentGeneration;
import com.ecommerce.agent.copyseo.CopySeoAgentException;
import com.ecommerce.agent.copyseo.CopySeoAgentGateway;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** 通过 HTTP 调用 Python 文案与 SEO Agent。 */
@Component
public class PythonCopySeoAgentClient implements CopySeoAgentGateway {
    private final RestClient restClient;

    @Autowired
    public PythonCopySeoAgentClient(PythonServiceProperties properties) {
        this(RestClient.builder().baseUrl(properties.baseUrl().toString()).build());
    }

    PythonCopySeoAgentClient(RestClient restClient) {
        // 允许测试传入带 Mock 服务器的 RestClient。
        this.restClient = restClient;
    }

    @Override
    public AgentGeneration generate(String sourceProductId, String targetMarket, String language) {
        try {
            // Python 返回的 JSON 会直接反序列化为统一的 AgentGeneration。
            return restClient.post()
                    .uri("/internal/agents/copy-seo/generations")
                    .body(Map.of(
                            "sourceProductId", sourceProductId,
                            "targetMarket", targetMarket,
                            "language", language))
                    .retrieve()
                    .body(AgentGeneration.class);
        } catch (RestClientResponseException exception) {
            // 将 Python 的结构化错误转换成 Java 业务异常，交给 Service 保存失败记录。
            ErrorEnvelope envelope = exception.getResponseBodyAs(ErrorEnvelope.class);
            ErrorDetail detail = envelope == null ? null : envelope.detail();
            String errorType = detail == null || detail.errorCode() == null
                    ? "PYTHON_AGENT_ERROR" : detail.errorCode();
            throw new CopySeoAgentException(
                    errorType, detail != null && detail.retryable(), exception);
        }
    }

    /** Python 错误响应的最外层结构。 */
    private record ErrorEnvelope(ErrorDetail detail) {
    }

    /** Python 错误响应中的具体错误信息。 */
    private record ErrorDetail(String errorCode, boolean retryable) {
    }
}
