package com.moamap.place.ai.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedPlaceTest {

    @Test
    void toSearchKeyword는_region과_name을_합쳐서_반환한다() {
        ExtractedPlace place = new ExtractedPlace("프루", "북촌");

        assertThat(place.toSearchKeyword()).isEqualTo("북촌 프루");
    }

    @Test
    void toSearchKeyword는_region이_없으면_name만_반환한다() {
        ExtractedPlace place = new ExtractedPlace("프루", null);

        assertThat(place.toSearchKeyword()).isEqualTo("프루");
    }

    @Test
    void toSearchKeyword는_region이_비어있으면_name만_반환한다() {
        ExtractedPlace place = new ExtractedPlace("프루", "  ");

        assertThat(place.toSearchKeyword()).isEqualTo("프루");
    }

    @Test
    void toSearchKeyword는_name이_null이어도_예외없이_region만_반환한다() {
        ExtractedPlace place = new ExtractedPlace(null, "북촌");

        assertThat(place.toSearchKeyword()).isEqualTo("북촌");
    }

    @Test
    void toSearchKeyword는_name과_region이_모두_null이면_빈_문자열을_반환한다() {
        ExtractedPlace place = new ExtractedPlace(null, null);

        assertThat(place.toSearchKeyword()).isEmpty();
    }
}
