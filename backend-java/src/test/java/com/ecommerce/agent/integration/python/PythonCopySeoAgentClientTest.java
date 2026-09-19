package com.ecommerce.agent.integration.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.ecommerce.agent.copyseo.CopySeoAgentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonCopySeoAgentClientTest {

    @Test
    void generatesStructuredCopyThroughThePythonAgentBoundary() {
        var builder = RestClient.builder().baseUrl("http://localhost:8005");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PythonCopySeoAgentClient(builder.build());
        server.expect(requestTo("http://localhost:8005/internal/agents/copy-seo/generations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"sourceProductId":"fake-desk-lamp-001","targetMarket":"US","language":"en"}
                        """))
                .andRespond(withSuccess(responseBody(), MediaType.APPLICATION_JSON));

        var result = client.generate("fake-desk-lamp-001", "US", "en");

        assertThat(result.generationId())
                .isEqualTo("9d1625ec-9c39-45c8-afc0-24e2e45b2158");
        assertThat(result.reviewStatus().name()).isEqualTo("PENDING_REVIEW");
        assertThat(result.content().seoTitle()).isEqualTo("Adjustable LED Desk Lamp");
        server.verify();
    }

    @Test
    void mapsStructuredPythonFailureWithoutExposingUpstreamBody() {
        var builder = RestClient.builder().baseUrl("http://localhost:8005");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PythonCopySeoAgentClient(builder.build());
        server.expect(requestTo("http://localhost:8005/internal/agents/copy-seo/generations"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail":{"errorCode":"TIMEOUT","retryable":true}}
                                """));

        assertThatThrownBy(() -> client.generate("source-1", "US", "en"))
                .isInstanceOfSatisfying(CopySeoAgentException.class, error -> {
                    assertThat(error.errorType()).isEqualTo("TIMEOUT");
                    assertThat(error.retryable()).isTrue();
                });
        server.verify();
    }

    private String responseBody() {
        return """
                {
                  "generationId":"9d1625ec-9c39-45c8-afc0-24e2e45b2158",
                  "sourceProductId":"fake-desk-lamp-001",
                  "status":"SUCCEEDED",
                  "reviewStatus":"PENDING_REVIEW",
                  "content":{
                    "title":"Adjustable LED Desk Lamp",
                    "bulletPoints":["Adjustable lighting"],
                    "description":"A generated description.",
                    "seoTitle":"Adjustable LED Desk Lamp",
                    "metaDescription":"A compact desk lamp.",
                    "tags":["desk lamp"]
                  },
                  "execution":{
                    "agent":"COPY_SEO",
                    "agentVersion":"fake-v1",
                    "promptVersion":"copy-seo-v1",
                    "model":"fake-deterministic",
                    "durationMs":0
                  }
                }
                """;
    }
}
