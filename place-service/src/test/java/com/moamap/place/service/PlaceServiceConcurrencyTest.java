package com.moamap.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.BDDMockito.given;

/**
 * 실제 H2 DB에 대해 동시 요청을 발생시켜, uk_places_map_kakao_place 유니크 제약이
 * checkDuplicate의 SELECT-then-INSERT race condition을 실제로 막아주는지 검증한다.
 */
@DataJpaTest
@Import({PlaceService.class, PlaceBulkRegistrar.class})
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
class PlaceServiceConcurrencyTest {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceService placeService;

    @MockitoBean
    private MapClient mapClient;

    private PlaceCreateRequest createRequest() {
        return new PlaceCreateRequest(
            "스타벅스 강남점",
            "서울 강남구 테헤란로 1",
            "서울 강남구 테헤란로 1",
            BigDecimal.valueOf(37.497852),
            BigDecimal.valueOf(127.027618),
            "카페",
            "26338954",
            PlaceSourceType.KAKAO_SEARCH,
            null,
            null,
            10L,
            null
        );
    }

    @Test
    void 같은_장소를_동시에_등록해도_한_건만_저장된다() throws InterruptedException {
        // given
        given(mapClient.getMemberInfo(10L, 1L)).willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateRejectedCount = new AtomicInteger();

        // when: threadCount개의 요청이 최대한 동시에 같은 (mapId, kakaoPlaceId)로 등록을 시도한다
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startSignal.await();
                    placeService.create(createRequest(), 1L);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == PlaceErrorCode.DUPLICATE_PLACE) {
                        duplicateRejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }
        startSignal.countDown();
        doneSignal.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 정확히 한 건만 성공하고, 나머지는 모두 DUPLICATE_PLACE로 거부되며, DB에도 한 건만 남는다
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateRejectedCount.get()).isEqualTo(threadCount - 1);
        assertThat(placeRepository.count()).isEqualTo(1);
    }
}
