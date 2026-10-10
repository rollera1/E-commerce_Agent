package com.ecommerce.agent.copyseo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.agent.productselection.CandidateIdListConverter;
import jakarta.persistence.AttributeConverter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StringListJsonConverterTest {
    static Stream<AttributeConverter<List<String>, String>> converters() {
        return Stream.of(new StringListJsonConverter(), new CandidateIdListConverter());
    }

    @ParameterizedTest
    @MethodSource("converters")
    void preservesExistingStorageFormatAndUnicode(AttributeConverter<List<String>, String> converter) {
        assertThat(converter.convertToDatabaseColumn(List.of("a", "b"))).isEqualTo("YQ==,Yg==");
        var values = List.of("商品,标题\n", "", " ", "末尾", "");
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(values)))
                .containsExactlyElementsOf(values);
    }

    @ParameterizedTest
    @MethodSource("converters")
    void preservesEmptyAndNullDatabaseValues(AttributeConverter<List<String>, String> converter) {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToDatabaseColumn(List.of())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("converters")
    void rejectsCorruptStorage(AttributeConverter<List<String>, String> converter) {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-base64!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("converters")
    void preservesNullInputRejection(AttributeConverter<List<String>, String> converter) {
        assertThatThrownBy(() -> converter.convertToDatabaseColumn(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> converter.convertToDatabaseColumn(Arrays.asList("a", null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void preservesDifferentWhitespacePolicies() {
        assertThat(new CandidateIdListConverter().convertToEntityAttribute(" \t\n")).isEmpty();
        assertThatThrownBy(() -> new StringListJsonConverter().convertToEntityAttribute(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
