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
 * PlaceCreateRequest.photoUrls의 @Size(max=5) 개수 제약이 Bean Validation 경계에서
 * 실제로 걸리는지 검증한다(청사진 3-5). 개수 상한 위반에는 전용 ErrorCode가 없고
 * 표준 검증 경로(MethodArgumentNotValidException → COMMON_001)를 재사용하므로, 여기서는
 * ConstraintViolation 발생 여부까지만 확인한다(HTTP 400 변환은 GlobalExceptionHandler가 이미 검증됨).
 */
class PlaceCreateRequestTest {

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

    private PlaceCreateRequest requestWithPhotoUrls(List<String> photoUrls) {
        return new PlaceCreateRequest(
            "스타벅스 강남점", "서울 강남구 테헤란로 1", "서울 강남구 테헤란로 1",
            BigDecimal.valueOf(37.497852), BigDecimal.valueOf(127.027618), "카페", "26338954",
            PlaceSourceType.KAKAO_SEARCH, null, null, 10L, null, photoUrls
        );
    }

    @Test
    void photoUrls가_없어도_유효하다() {
        // when
        Set<ConstraintViolation<PlaceCreateRequest>> violations = validator.validate(requestWithPhotoUrls(null));

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void photoUrls가_1장이면_유효하다() {
        // when
        Set<ConstraintViolation<PlaceCreateRequest>> violations =
            validator.validate(requestWithPhotoUrls(List.of("https://cdn.moamap.com/places/10/a.jpg")));

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void photoUrls가_5장이면_유효하다() {
        // given
        List<String> fivePhotos = List.of(
            "https://cdn.moamap.com/places/10/1.jpg",
            "https://cdn.moamap.com/places/10/2.jpg",
            "https://cdn.moamap.com/places/10/3.jpg",
            "https://cdn.moamap.com/places/10/4.jpg",
            "https://cdn.moamap.com/places/10/5.jpg"
        );

        // when
        Set<ConstraintViolation<PlaceCreateRequest>> violations = validator.validate(requestWithPhotoUrls(fivePhotos));

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void photoUrls가_6장이면_Size위반이_발생한다() {
        // given
        List<String> sixPhotos = List.of(
            "https://cdn.moamap.com/places/10/1.jpg",
            "https://cdn.moamap.com/places/10/2.jpg",
            "https://cdn.moamap.com/places/10/3.jpg",
            "https://cdn.moamap.com/places/10/4.jpg",
            "https://cdn.moamap.com/places/10/5.jpg",
            "https://cdn.moamap.com/places/10/6.jpg"
        );

        // when
        Set<ConstraintViolation<PlaceCreateRequest>> violations = validator.validate(requestWithPhotoUrls(sixPhotos));

        // then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("photoUrls");
    }

    @Test
    void photoUrl_원소가_1000자이면_유효하다() {
        // given
        String url1000Chars = "https://cdn.moamap.com/" + "a".repeat(1000 - "https://cdn.moamap.com/".length());

        // when
        Set<ConstraintViolation<PlaceCreateRequest>> violations =
            validator.validate(requestWithPhotoUrls(List.of(url1000Chars)));

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void photoUrl_원소가_1001자이면_Size위반이_발생한다() {
        // given
        String url1001Chars = "https://cdn.moamap.com/" + "a".repeat(1001 - "https://cdn.moamap.com/".length());

        // when
        Set<ConstraintViolation<PlaceCreateRequest>> violations =
            validator.validate(requestWithPhotoUrls(List.of(url1001Chars)));

        // then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("photoUrls[0].<list element>");
    }
}
