package com.moamap.map.dto;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.moamap.map.entity.GeocodeStatus;
import com.moamap.map.entity.PublicRestroom;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRestroomDetailResponseTest {

    private PublicRestroom fullRestroom() {
        return PublicRestroom.builder()
            .id(1L)
            .mngNo("mng-1")
            .name("역삼동 공중화장실")
            .category("공중화장실")
            .ownerType("공공")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 123-4")
            .lat(BigDecimal.valueOf(37.5))
            .lng(BigDecimal.valueOf(127.0))
            .maleToilet((short) 1)
            .maleUrinal((short) 2)
            .maleDisabledToilet((short) 1)
            .maleDisabledUrinal((short) 0)
            .maleChildToilet((short) 0)
            .maleChildUrinal((short) 0)
            .femaleToilet((short) 3)
            .femaleDisabledToilet((short) 1)
            .femaleChildToilet((short) 0)
            .openHours("상시")
            .openHoursDetail("24시간")
            .diaperTable(true)
            .diaperTableLocation("입구")
            .emergencyBell(true)
            .emergencyBellLocation("내부")
            .entranceCctv(true)
            .wasteDisposal("종량제")
            .managerOrg("강남구청")
            .phone("02-1234-5678")
            .installedYm("2020-01")
            .remodeledYm("2023-05")
            .sourceModifiedAt(LocalDateTime.of(2026, 9, 11, 17, 54, 31))
            .dataRefDate(LocalDate.of(2026, 9, 11))
            .geocodeStatus(GeocodeStatus.OK)
            .lastSyncedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void 상세_응답은_엔티티의_모든_공개_필드를_빠짐없이_매핑한다() {
        PublicRestroom restroom = fullRestroom();

        PublicRestroomDetailResponse response = PublicRestroomDetailResponse.from(restroom);

        assertThat(response.id()).isEqualTo(restroom.getId());
        assertThat(response.mngNo()).isEqualTo(restroom.getMngNo());
        assertThat(response.name()).isEqualTo(restroom.getName());
        assertThat(response.category()).isEqualTo(restroom.getCategory());
        assertThat(response.ownerType()).isEqualTo(restroom.getOwnerType());
        assertThat(response.roadAddress()).isEqualTo(restroom.getRoadAddress());
        assertThat(response.lotAddress()).isEqualTo(restroom.getLotAddress());
        assertThat(response.lat()).isEqualTo(restroom.getLat());
        assertThat(response.lng()).isEqualTo(restroom.getLng());
        assertThat(response.maleToilet()).isEqualTo(restroom.getMaleToilet());
        assertThat(response.maleUrinal()).isEqualTo(restroom.getMaleUrinal());
        assertThat(response.maleDisabledToilet()).isEqualTo(restroom.getMaleDisabledToilet());
        assertThat(response.maleDisabledUrinal()).isEqualTo(restroom.getMaleDisabledUrinal());
        assertThat(response.maleChildToilet()).isEqualTo(restroom.getMaleChildToilet());
        assertThat(response.maleChildUrinal()).isEqualTo(restroom.getMaleChildUrinal());
        assertThat(response.femaleToilet()).isEqualTo(restroom.getFemaleToilet());
        assertThat(response.femaleDisabledToilet()).isEqualTo(restroom.getFemaleDisabledToilet());
        assertThat(response.femaleChildToilet()).isEqualTo(restroom.getFemaleChildToilet());
        assertThat(response.openHours()).isEqualTo(restroom.getOpenHours());
        assertThat(response.openHoursDetail()).isEqualTo(restroom.getOpenHoursDetail());
        assertThat(response.diaperTable()).isEqualTo(restroom.isDiaperTable());
        assertThat(response.diaperTableLocation()).isEqualTo(restroom.getDiaperTableLocation());
        assertThat(response.emergencyBell()).isEqualTo(restroom.isEmergencyBell());
        assertThat(response.emergencyBellLocation()).isEqualTo(restroom.getEmergencyBellLocation());
        assertThat(response.entranceCctv()).isEqualTo(restroom.isEntranceCctv());
        assertThat(response.wasteDisposal()).isEqualTo(restroom.getWasteDisposal());
        assertThat(response.managerOrg()).isEqualTo(restroom.getManagerOrg());
        assertThat(response.phone()).isEqualTo(restroom.getPhone());
        assertThat(response.installedYm()).isEqualTo(restroom.getInstalledYm());
        assertThat(response.remodeledYm()).isEqualTo(restroom.getRemodeledYm());
        assertThat(response.dataRefDate()).isEqualTo(restroom.getDataRefDate());
    }

    @Test
    void 내부_동기화_메타데이터_필드는_응답에_존재하지_않는다() {
        Set<String> fieldNames = Arrays.stream(PublicRestroomDetailResponse.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertThat(fieldNames)
            .doesNotContain("geocodeStatus", "lastSyncedAt", "sourceModifiedAt", "deletedAt");
    }
}
