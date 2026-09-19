package com.ecommerce.agent.copyseo;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/** 在 Java 字符串列表和数据库 TEXT 字段之间转换。 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {
    @Override
    public String convertToDatabaseColumn(List<String> value) {
        // 每项先编码再用逗号分隔，避免原字符串中的逗号或换行破坏数据格式。
        return value.stream()
                .map(item -> Base64.getEncoder().encodeToString(
                        item.getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.joining(","));
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        // 按分隔符拆开后逐项解码，恢复原始字符串列表。
        return List.of(value.split(",", -1)).stream()
                .map(item -> new String(Base64.getDecoder().decode(item), StandardCharsets.UTF_8))
                .toList();
    }
}
