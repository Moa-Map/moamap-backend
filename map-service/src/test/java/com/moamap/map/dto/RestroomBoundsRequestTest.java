package com.moamap.map.dto;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RestroomBoundsRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private RestroomBoundsRequest request(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {
        return new RestroomBoundsRequest(swLat, swLng, neLat, neLng);
    }

    @ParameterizedTest
    @CsvSource({
        "-90.0, -180.0, 90.0, 180.0",
        "0, 0, 0, 0",
    })
    void 경계값_이내는_위반이_없다(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {
        Set<ConstraintViolation<RestroomBoundsRequest>> violations =
            validator.validate(request(swLat, swLng, neLat, neLng));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "-90.1, 0, 0, 0",
        "90.1, 0, 0, 0",
        "0, -180.1, 0, 0",
        "0, 180.1, 0, 0",
        "0, 0, -90.1, 0",
        "0, 0, 90.1, 0",
        "0, 0, 0, -180.1",
        "0, 0, 0, 180.1",
    })
    void 위경도_범위를_벗어나면_위반이_있다(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {
        Set<ConstraintViolation<RestroomBoundsRequest>> violations =
            validator.validate(request(swLat, swLng, neLat, neLng));

        assertThat(violations).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {
        "null, 0, 0, 0",
        "0, null, 0, 0",
        "0, 0, null, 0",
        "0, 0, 0, null",
    }, nullValues = "null")
    void 필수값이_없으면_위반이_있다(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {
        Set<ConstraintViolation<RestroomBoundsRequest>> violations =
            validator.validate(request(swLat, swLng, neLat, neLng));

        assertThat(violations).isNotEmpty();
    }
}
