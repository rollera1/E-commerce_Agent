package com.ecommerce.agent.copyseo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentTraceJsonConverterTest {

    private final AgentTraceJsonConverter converter = new AgentTraceJsonConverter();

    @Test
    void roundTripsTraceWithoutLosingToolSummary() {
        var trace = new AgentTrace(
                "AWAIT_MERCHANT_REVIEW",
                AgentOutcome.COMPLETED,
                List.of("COLLECT_PRODUCT_CONTEXT", "AWAIT_MERCHANT_REVIEW"),
                List.of(new ToolCallTrace(
                        "get_product_detail",
                        ToolCallStatus.SUCCEEDED,
                        3,
                        Map.of("variantCount", 1, "source", "FAKE"),
                        null)),
                1,
                0,
                List.of(),
                "MERCHANT_REVIEW");

        var restored = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(trace));

        assertThat(restored).isEqualTo(trace);
        assertThat(restored.toolCalls().getFirst().summary())
                .containsEntry("variantCount", 1);
    }

    @Test
    void keepsNullForLegacyRowsWithoutTrace() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
