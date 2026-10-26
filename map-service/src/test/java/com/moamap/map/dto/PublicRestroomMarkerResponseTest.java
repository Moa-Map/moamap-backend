package com.moamap.map.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.moamap.map.entity.GeocodeStatus;
import com.moamap.map.entity.PublicRestroom;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRestroomMarkerResponseTest {

    private PublicRestroom restroomWithDisabledFacilities(short maleDisabledToilet, short maleDisabledUrinal,
        short femaleDisabledToilet) {
        return PublicRestroom.builder()
            .id(1L)
            .mngNo("mng-1")
            .name("역삼동 공중화장실")
            .category("공중화장실")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lat(BigDecimal.valueOf(37.5))
            .lng(BigDecimal.valueOf(127.0))
            .maleToilet((short) 1)
            .maleUrinal((short) 1)
            .maleDisabledToilet(maleDisabledToilet)
            .maleDisabledUrinal(maleDisabledUrinal)
            .maleChildToilet((short) 0)
            .maleChildUrinal((short) 0)
            .femaleToilet((short) 1)
            .femaleDisabledToilet(femaleDisabledToilet)
            .femaleChildToilet((short) 0)
            .openHours("상시")
            .diaperTable(false)
            .emergencyBell(false)
            .entranceCctv(false)
            .geocodeStatus(GeocodeStatus.OK)
            .lastSyncedAt(LocalDateTime.now())
            .build();
    }

    @ParameterizedTest
    @CsvSource({
        "1, 0, 0, true",
        "0, 1, 0, true",
        "0, 0, 1, true",
        "0, 0, 0, false",
    })
    void 장애인_화장실_보유_여부는_세_컬럼_중_하나라도_있으면_true다(
        short maleDisabledToilet, short maleDisabledUrinal, short femaleDisabledToilet, boolean expected) {
        PublicRestroom restroom = restroomWithDisabledFacilities(maleDisabledToilet, maleDisabledUrinal,
            femaleDisabledToilet);

        PublicRestroomMarkerResponse response = PublicRestroomMarkerResponse.from(restroom);

        assertThat(response.disabledAccessible()).isEqualTo(expected);
        assertThat(response.id()).isEqualTo(restroom.getId());
        assertThat(response.name()).isEqualTo(restroom.getName());
        assertThat(response.lat()).isEqualTo(restroom.getLat());
        assertThat(response.lng()).isEqualTo(restroom.getLng());
        assertThat(response.category()).isEqualTo(restroom.getCategory());
        assertThat(response.openHours()).isEqualTo(restroom.getOpenHours());
    }
}
