package com.ecommerce.agent.productselection;

import com.ecommerce.agent.copyseo.StringListJsonConverter;
import jakarta.persistence.Converter;
import java.util.List;

/** 在候选商品 ID 列表和数据库 TEXT 字段之间转换。 */
@Converter
public class CandidateIdListConverter extends StringListJsonConverter {
    @Override
    public List<String> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return super.convertToEntityAttribute(value);
    }
}
