package com.ecommerce.agent.copyseo;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 在 AgentTrace 和数据库 TEXT 字段之间转换。 */
@Converter
public class AgentTraceJsonConverter implements AttributeConverter<AgentTrace, String> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(AgentTrace trace) {
        if (trace == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(trace);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化 Agent 执行轨迹", exception);
        }
    }

    @Override
    public AgentTrace convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(value, AgentTrace.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法读取 Agent 执行轨迹", exception);
        }
    }
}
