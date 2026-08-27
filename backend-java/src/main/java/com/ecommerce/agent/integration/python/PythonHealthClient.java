package com.ecommerce.agent.integration.python;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonHealthClient {

    private final RestClient restClient;

    public PythonHealthClient(PythonServiceProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    public PythonHealthResponse checkHealth() {
        return restClient.get()
                .uri("/health")
                .retrieve()
                .body(PythonHealthResponse.class);
    }
}
