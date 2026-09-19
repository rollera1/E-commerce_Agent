package com.ecommerce.agent.integration.python;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 调用 Python 服务健康检查接口的 HTTP 客户端。 */
@Component
public class PythonHealthClient {

    private final RestClient restClient;

    public PythonHealthClient(PythonServiceProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    public PythonHealthResponse checkHealth() {
        // baseUrl 来自配置，这里只补充 Python 健康检查路径。
        return restClient.get()
                .uri("/health")
                .retrieve()
                .body(PythonHealthResponse.class);
    }
}
