package com.moamap.map.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RestroomAddressNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiterString = "|", value = {
        "서울특별시 서초구 강남대로 224 (양재동, 양재한신휴플러스)|서울특별시 서초구 강남대로 224",
        "서울특별시 강남구 테헤란로 1, 2층|서울특별시 강남구 테헤란로 1",
        "서울특별시 강남구 테헤란로 1 (역삼동)|서울특별시 강남구 테헤란로 1",
    })
    void 도로명_주소는_콤마나_괄호_중_먼저_나오는_지점에서_자른다(String input, String expected) {
        assertThat(RestroomAddressNormalizer.normalizeRoadAddress(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiterString = "|", value = {
        "경기도 시흥시 정왕동 1368 소망공원 내 공중화장실2|경기도 시흥시 정왕동 1368",
        "강원특별자치도 철원군 갈말읍 신철원리 산 26-38|강원특별자치도 철원군 갈말읍 신철원리 산 26-38",
        "서울특별시 강남구 역삼1동 123-4 빌딩|서울특별시 강남구 역삼1동 123-4",
    })
    void 지번_주소는_번지까지만_남기고_뒤를_잘라낸다(String input, String expected) {
        assertThat(RestroomAddressNormalizer.normalizeLotAddress(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"세종특별자치시", "abc"})
    void 정규식이_미매치면_원본_그대로_반환한다(String input) {
        assertThat(RestroomAddressNormalizer.normalizeRoadAddress(input)).isEqualTo(input);
        assertThat(RestroomAddressNormalizer.normalizeLotAddress(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void null_또는_빈_문자열은_그대로_반환한다(String input) {
        assertThat(RestroomAddressNormalizer.normalizeRoadAddress(input)).isEqualTo(input);
        assertThat(RestroomAddressNormalizer.normalizeLotAddress(input)).isEqualTo(input);
    }
}
