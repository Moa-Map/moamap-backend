package com.moamap.map.service;

import java.util.List;
import com.moamap.map.entity.FootTrafficArea;
import com.moamap.map.client.SeoulCityDataClient;
import com.moamap.map.repository.FootTrafficCongestionRepository;
import com.moamap.map.repository.FootTrafficAreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 유동인구 관측지점 121곳을 순회하며 서울시 실시간 인구 데이터를 가져와 최신 스냅샷으로 upsert한다.
 * 장소 하나가 실패해도 나머지는 계속 진행하고, 실패한 곳은 이전 스냅샷을 그대로 둔다.
 *
 * 순차 호출 121회(수십 초)라 배치 전체를 하나의 트랜잭션으로 묶지 않는다 — 저장은 건별로 커밋된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootTrafficCongestionSyncService {

    private final FootTrafficAreaRepository footTrafficAreaRepository;
    private final FootTrafficCongestionRepository congestionRepository;
    private final SeoulCityDataClient seoulCityDataClient;

    public void syncAll() {
        List<FootTrafficArea> footTrafficAreas = footTrafficAreaRepository.findAll();
        int successCount = 0;

        for (FootTrafficArea footTrafficArea : footTrafficAreas) {
            if (syncOne(footTrafficArea)) {
                successCount++;
            }
        }

        log.info("유동인구 관측지점 혼잡도 동기화 완료: {}/{}", successCount, footTrafficAreas.size());
    }

    private boolean syncOne(FootTrafficArea footTrafficArea) {
        try {
            congestionRepository.save(seoulCityDataClient.fetch(footTrafficArea.getFootTrafficAreaCd()));
            return true;
        } catch (Exception e) {
            log.warn("혼잡도 갱신 실패: footTrafficAreaCd={}", footTrafficArea.getFootTrafficAreaCd(), e);
            return false;
        }
    }
}
