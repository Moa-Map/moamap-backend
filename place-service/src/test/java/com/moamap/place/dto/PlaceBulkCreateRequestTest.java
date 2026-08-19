package com.moamap.place.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import com.moamap.place.entity.PlaceSourceType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일괄 등록 항목의 photoUrls 제약이 단건 등록(PlaceCreateRequest)과 같은지 검증한다.
 *
 * 항목 검증은 places에 붙은 @Valid를 타고 내려가야 걸린다. 요청 단위로 검증해서
 * 중첩 위반이 실제로 밖까지 올라오는지(경로가 places[i].photoUrls로 잡히는지)까지 확인한다.
 */
class PlaceBulkCreateRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    private PlaceBulkCreateRequest requestWithPhotoUrls(List<String> photoUrls) {
        PlaceBulkCreateRequest.Item item = new PlaceBulkCreateRequest.Item(
            "스타벅스 강남점", "서울 강남구 테헤란로 1", "서울 강남구 테헤란로 1",
            BigDecimal.valueOf(37.497852), BigDecimal.valueOf(127.027618), "카페", "26338954",
            PlaceSourceType.KAKAO_SEARCH, null, null, null, photoUrls
        );
        return new PlaceBulkCreateRequest(10L, List.of(item));
    }

    private List<String> photos(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
            .mapToObj(i -> "https://cdn.moamap.com/places/10/" + i + ".jpg")
            .toList();
    }

    @Test
    void 항목에_photoUrls가_없어도_유효하다() {
        Set<ConstraintViolation<PlaceBulkCreateRequest>> violations =
            validator.validate(requestWithPhotoUrls(null));

        assertThat(violations).isEmpty();
    }

    @Test
    void 항목의_photoUrls가_5장이면_유효하다() {
        Set<ConstraintViolation<PlaceBulkCreateRequest>> violations =
            validator.validate(requestWithPhotoUrls(photos(5)));

        assertThat(violations).isEmpty();
    }

    @Test
    void 항목의_photoUrls가_6장이면_Size위반이_발생한다() {
        Set<ConstraintViolation<PlaceBulkCreateRequest>> violations =
            validator.validate(requestWithPhotoUrls(photos(6)));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath())
            .hasToString("places[0].photoUrls");
    }

    @Test
    void 항목의_photoUrl_원소가_1001자이면_Size위반이_발생한다() {
        String prefix = "https://cdn.moamap.com/";
        String url1001Chars = prefix + "a".repeat(1001 - prefix.length());

        Set<ConstraintViolation<PlaceBulkCreateRequest>> violations =
            validator.validate(requestWithPhotoUrls(List.of(url1001Chars)));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath())
            .hasToString("places[0].photoUrls[0].<list element>");
    }
}
