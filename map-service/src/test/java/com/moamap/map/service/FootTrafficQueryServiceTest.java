package com.moamap.map.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.moamap.map.dto.FootTrafficAreaResponse;
import com.moamap.map.dto.FootTrafficCongestionResponse;
import com.moamap.map.entity.FootTrafficArea;
import com.moamap.map.entity.FootTrafficCongestion;
import com.moamap.map.repository.FootTrafficAreaRepository;
import com.moamap.map.repository.FootTrafficCongestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 실제 API가 반환하는 응답 모양(DTO 매핑)이 맞는지 검증. repository는 Mockito로 흉내 낸다.
 */
@ExtendWith(MockitoExtension.class)
class FootTrafficQueryServiceTest {

    @Mock
    private FootTrafficAreaRepository footTrafficAreaRepository;

    @Mock
    private FootTrafficCongestionRepository congestionRepository;

    @InjectMocks
    private FootTrafficQueryService queryService;

    @Test
    void findAllAreas는_장소_정보를_그대로_DTO로_변환한다() {
        // Arrange
        given(footTrafficAreaRepository.findAll())
            .willReturn(List.of(newFootTrafficArea("POI001", "강남 MICE 관광특구", "관광특구")));

        // Act
        List<FootTrafficAreaResponse> result = queryService.findAllAreas();

        // Assert
        assertThat(result).hasSize(1);
        FootTrafficAreaResponse response = result.get(0);
        assertThat(response.footTrafficAreaCd()).isEqualTo("POI001");
        assertThat(response.areaNm()).isEqualTo("강남 MICE 관광특구");
        assertThat(response.category()).isEqualTo("관광특구");
        assertThat(response.lat()).isEqualByComparingTo("37.510897");
        assertThat(response.lng()).isEqualByComparingTo("127.059949");
    }

    @Test
    void findAllCongestion은_혼잡도_스냅샷을_그대로_DTO로_변환한다() {
        // Arrange
        FootTrafficCongestion congestion = FootTrafficCongestion.builder()
            .footTrafficAreaCd("POI001")
            .congestLvl("약간 붐빔")
            .ppltnMin(26000)
            .ppltnMax(28000)
            .build();
        given(congestionRepository.findAll()).willReturn(List.of(congestion));

        // Act
        List<FootTrafficCongestionResponse> result = queryService.findAllCongestion();

        // Assert
        assertThat(result).hasSize(1);
        FootTrafficCongestionResponse response = result.get(0);
        assertThat(response.footTrafficAreaCd()).isEqualTo("POI001");
        assertThat(response.congestLvl()).isEqualTo("약간 붐빔");
        assertThat(response.ppltnMin()).isEqualTo(26000);
        assertThat(response.ppltnMax()).isEqualTo(28000);
    }

    @Test
    void 저장된_장소가_없으면_findAllAreas는_빈_리스트를_반환한다() {
        // Arrange
        given(footTrafficAreaRepository.findAll()).willReturn(List.of());

        // Act
        List<FootTrafficAreaResponse> result = queryService.findAllAreas();

        // Assert
        assertThat(result).isEmpty();
    }

    private FootTrafficArea newFootTrafficArea(String cd, String areaNm, String category) {
        FootTrafficArea area = BeanUtils.instantiateClass(FootTrafficArea.class);
        ReflectionTestUtils.setField(area, "footTrafficAreaCd", cd);
        ReflectionTestUtils.setField(area, "areaNm", areaNm);
        ReflectionTestUtils.setField(area, "engNm", "Test Zone");
        ReflectionTestUtils.setField(area, "category", category);
        ReflectionTestUtils.setField(area, "lat", new BigDecimal("37.510897"));
        ReflectionTestUtils.setField(area, "lng", new BigDecimal("127.059949"));
        ReflectionTestUtils.setField(area, "createdAt", LocalDateTime.now());
        return area;
    }
}
