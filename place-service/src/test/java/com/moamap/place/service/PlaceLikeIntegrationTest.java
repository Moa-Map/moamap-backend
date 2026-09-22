package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.moamap.place.dto.PlaceLikeResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceLikeRepository;
import com.moamap.place.repository.PlaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 하트의 멱등성과 동시성을 실제 H2에 대고 확인한다. 모의 객체로는 증명할 수 없는 지점이다.
 *
 * 쓰기가 REQUIRES_NEW로 실제 커밋되므로 테스트 트랜잭션을 두지 않는다(NOT_SUPPORTED).
 * 롤백으로 정리되지 않으니 @AfterEach에서 직접 지운다.
 */
@DataJpaTest
@Import({PlaceLikeService.class, PlaceLikeWriter.class})
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlaceLikeIntegrationTest {

    private static final Long MAP_ID = 10L;
    private static final Long USER_ID = 2L;

    @Autowired
    private PlaceLikeService placeLikeService;

    @Autowired
    private PlaceLikeRepository placeLikeRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @MockitoBean
    private MapClient mapClient;

    private Long placeId;

    @BeforeEach
    void setUp() {
        placeId = placeRepository.saveAndFlush(Place.builder()
            .name("스타벅스 강남점")
            .lat(BigDecimal.valueOf(37.497852))
            .lng(BigDecimal.valueOf(127.027618))
            .kakaoPlaceId("26338954")
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(MAP_ID)
            .createdBy(1L)
            .build()).getId();
    }

    @AfterEach
    void cleanUp() {
        placeLikeRepository.deleteAll();
        placeRepository.deleteAll();
    }

    private void givenMember(Long userId) {
        given(mapClient.getMemberInfo(MAP_ID, userId))
            .willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
    }

    @Test
    void 하트를_누르면_저장되고_likeCount가_반영된다() {
        // given
        givenMember(USER_ID);

        // when
        PlaceLikeResponse response = placeLikeService.like(placeId, USER_ID);

        // then
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(1);
        assertThat(placeRepository.findById(placeId).orElseThrow().getLikeCount()).isEqualTo(1);
    }

    /** 더블탭·재시도로 같은 요청이 두 번 와도 상태가 흔들리면 안 된다. */
    @Test
    void 같은_사용자가_두_번_눌러도_한_건이고_에러가_아니다() {
        // given
        givenMember(USER_ID);

        // when
        placeLikeService.like(placeId, USER_ID);
        PlaceLikeResponse second = placeLikeService.like(placeId, USER_ID);

        // then
        assertThat(second.liked()).isTrue();
        assertThat(second.likeCount()).isEqualTo(1);
        assertThat(placeLikeRepository.count()).isEqualTo(1);
    }

    @Test
    void 취소하면_기록이_지워지고_likeCount가_줄어든다() {
        // given
        givenMember(USER_ID);
        placeLikeService.like(placeId, USER_ID);

        // when
        PlaceLikeResponse response = placeLikeService.unlike(placeId, USER_ID);

        // then
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isZero();
        assertThat(placeLikeRepository.count()).isZero();
        assertThat(placeRepository.findById(placeId).orElseThrow().getLikeCount()).isZero();
    }

    /** 누른 적 없는 상태의 취소도 에러가 아니다 — 목표 상태(안 눌림)는 이미 달성돼 있다. */
    @Test
    void 누르지_않은_상태에서_취소해도_에러가_아니다() {
        // given
        givenMember(USER_ID);

        // when
        PlaceLikeResponse response = placeLikeService.unlike(placeId, USER_ID);

        // then
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isZero();
    }

    @Test
    void 여러_사용자가_각각_누르면_사람_수만큼_센다() {
        // given
        List.of(2L, 3L, 4L).forEach(this::givenMember);

        // when
        List.of(2L, 3L, 4L).forEach(userId -> placeLikeService.like(placeId, userId));

        // then
        assertThat(placeRepository.findById(placeId).orElseThrow().getLikeCount()).isEqualTo(3);
    }

    /**
     * 같은 사용자의 요청이 동시에 겹쳐도 uk_place_likes_place_user가 두 건을 막고,
     * 모든 요청이 에러 없이 같은 상태로 답해야 한다.
     */
    @Test
    void 동시에_눌러도_한_건만_저장되고_모두_정상_응답한다() throws InterruptedException {
        // given
        givenMember(USER_ID);
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);
        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startSignal.await();
                    PlaceLikeResponse response = placeLikeService.like(placeId, USER_ID);
                    if (response.liked()) {
                        okCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }
        startSignal.countDown();
        doneSignal.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 아무도 에러를 받지 않고, 저장은 한 건이며, 카운트도 1이다
        assertThat(errorCount.get()).isZero();
        assertThat(okCount.get()).isEqualTo(threadCount);
        assertThat(placeLikeRepository.count()).isEqualTo(1);
        assertThat(placeRepository.findById(placeId).orElseThrow().getLikeCount()).isEqualTo(1);
    }

    @Test
    void findLikedPlaceIds는_내가_누른_장소만_준다() {
        // given
        Long otherPlaceId = placeRepository.saveAndFlush(Place.builder()
            .name("블루보틀 성수점")
            .lat(BigDecimal.valueOf(37.544))
            .lng(BigDecimal.valueOf(127.055))
            .kakaoPlaceId("26338955")
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(MAP_ID)
            .createdBy(1L)
            .build()).getId();
        givenMember(USER_ID);
        givenMember(3L);
        placeLikeService.like(placeId, USER_ID);
        placeLikeService.like(otherPlaceId, 3L);

        // when
        List<Long> liked = placeLikeRepository.findLikedPlaceIds(USER_ID, List.of(placeId, otherPlaceId));

        // then
        assertThat(liked).containsExactly(placeId);
    }
}
