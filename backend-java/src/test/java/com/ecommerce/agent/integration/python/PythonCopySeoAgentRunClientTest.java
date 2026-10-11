package com.ecommerce.agent.integration.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.agent.copyseo.AgentRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonCopySeoAgentRunClientTest {

    @Test
    void submitsWithStableBusinessIdAndAttemptIdempotencyKey() {
        var builder = RestClient.builder().baseUrl("http://localhost:8005");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PythonCopySeoAgentRunClient(builder.build());
        server.expect(requestTo("http://localhost:8005/internal/agent-runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "idempotencyKey":"copy-seo:generation-1:1",
                          "agentType":"COPY_SEO",
                          "businessObjectType":"COPY_SEO_GENERATION",
                          "businessObjectId":"generation-1",
                          "input":{
                            "sourceProductId":"source-1",
                            "targetMarket":"US",
                            "language":"en"
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {"runId":"run-1","status":"SUBMITTED","output":null,"error":null}
                        """, MediaType.APPLICATION_JSON));

        var result = client.submit("generation-1", 1, "source-1", "US", "en");

        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.status()).isEqualTo(AgentRunStatus.SUBMITTED);
        server.verify();
    }

    @Test
    void readsOnlyStructuredFailureFields() {
        var builder = RestClient.builder().baseUrl("http://localhost:8005");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PythonCopySeoAgentRunClient(builder.build());
        server.expect(requestTo("http://localhost:8005/internal/agent-runs/run-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "runId":"run-1",
                          "status":"FAILED",
                          "output":null,
                          "error":{
                            "errorType":"TIMEOUT",
                            "message":"Agent execution failed",
                            "retryable":true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.get("run-1");

        assertThat(result.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.failure().errorType()).isEqualTo("TIMEOUT");
        assertThat(result.failure().retryable()).isTrue();
        server.verify();
    }

    @Test
    void cancelsTheExactPythonRun() {
        var builder = RestClient.builder().baseUrl("http://localhost:8005");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new PythonCopySeoAgentRunClient(builder.build());
        server.expect(requestTo("http://localhost:8005/internal/agent-runs/run-1/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"runId":"run-1","status":"RUNNING","output":null,"error":null}
                        """, MediaType.APPLICATION_JSON));

        var result = client.cancel("run-1");

        assertThat(result.status()).isEqualTo(AgentRunStatus.RUNNING);
        server.verify();
    }
}
