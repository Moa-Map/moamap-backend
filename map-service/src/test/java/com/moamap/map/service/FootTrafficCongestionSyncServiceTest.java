package com.moamap.map.service;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.map.client.SeoulCityDataClient;
import com.moamap.map.entity.FootTrafficArea;
import com.moamap.map.entity.FootTrafficCongestion;
import com.moamap.map.repository.FootTrafficAreaRepository;
import com.moamap.map.repository.FootTrafficCongestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 실제 서울시 API는 호출하지 않는다 — 느리고, 키가 필요하고, 응답이 매번 바뀌어서
 * 테스트가 불안정해진다. 대신 SeoulCityDataClient를 Mockito로 흉내 내서
 * "API가 이런 값을 줬다고 치면, 우리 서비스가 DB에 뭘 저장하는가"만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FootTrafficCongestionSyncServiceTest {

    @Mock
    private FootTrafficAreaRepository footTrafficAreaRepository;

    @Mock
    private FootTrafficCongestionRepository congestionRepository;

    @Mock
    private SeoulCityDataClient seoulCityDataClient;

    @InjectMocks
    private FootTrafficCongestionSyncService syncService;

    @Test
    void 모든_관측지점을_순회하며_API에서_받은_데이터를_저장한다() {
        // Arrange
        given(footTrafficAreaRepository.findAll())
            .willReturn(List.of(newFootTrafficArea("POI001"), newFootTrafficArea("POI002")));
        given(seoulCityDataClient.fetch("POI001")).willReturn(newCongestion("POI001", "여유"));
        given(seoulCityDataClient.fetch("POI002")).willReturn(newCongestion("POI002", "보통"));

        // Act
        syncService.syncAll();

        // Assert — save()에 실제로 넘어간 값을 캡처해서 확인
        ArgumentCaptor<FootTrafficCongestion> captor = ArgumentCaptor.forClass(FootTrafficCongestion.class);
        verify(congestionRepository, times(2)).save(captor.capture());

        List<FootTrafficCongestion> saved = captor.getAllValues();
        assertThat(saved).extracting(FootTrafficCongestion::getFootTrafficAreaCd)
            .containsExactlyInAnyOrder("POI001", "POI002");
        assertThat(saved).extracting(FootTrafficCongestion::getCongestLvl)
            .containsExactlyInAnyOrder("여유", "보통");
    }

    @Test
    void 한_곳이_API_호출에서_실패해도_나머지는_계속_저장한다() {
        // Arrange
        given(footTrafficAreaRepository.findAll())
            .willReturn(List.of(newFootTrafficArea("POI001"), newFootTrafficArea("POI002")));
        given(seoulCityDataClient.fetch("POI001")).willThrow(new IllegalStateException("응답 없음"));
        given(seoulCityDataClient.fetch("POI002")).willReturn(newCongestion("POI002", "보통"));

        // Act — POI001에서 예외가 나도 밖으로 새어나가면 안 된다
        syncService.syncAll();

        // Assert — 실패한 POI001은 저장 자체가 시도되지 않고, POI002만 저장됨
        ArgumentCaptor<FootTrafficCongestion> captor = ArgumentCaptor.forClass(FootTrafficCongestion.class);
        verify(congestionRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getFootTrafficAreaCd()).isEqualTo("POI002");
    }

    private FootTrafficCongestion newCongestion(String cd, String congestLvl) {
        return FootTrafficCongestion.builder()
            .footTrafficAreaCd(cd)
            .congestLvl(congestLvl)
            .ppltnTime(LocalDateTime.now())
            .build();
    }

    private FootTrafficArea newFootTrafficArea(String cd) {
        FootTrafficArea area = BeanUtils.instantiateClass(FootTrafficArea.class);
        ReflectionTestUtils.setField(area, "footTrafficAreaCd", cd);
        ReflectionTestUtils.setField(area, "areaNm", "테스트 장소 " + cd);
        ReflectionTestUtils.setField(area, "category", "관광특구");
        ReflectionTestUtils.setField(area, "createdAt", LocalDateTime.now());
        return area;
    }
}
