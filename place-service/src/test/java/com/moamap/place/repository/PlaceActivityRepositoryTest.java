package com.moamap.place.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceReview;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

/**
 * 청사진 5장의 UNION ALL 네이티브 쿼리(PLACE_ADDED/PLACE_DELETED/REVIEW_CREATED 병합)를
 * 실제 쿼리로 태워서 검증한다. Mockito로는 쿼리 자체가 맞는지 알 수 없는 지점이다.
 *
 * default_schema를 비워 H2에서 검증하지만, 운영 Postgres는 {h-schema}placeholder로 동작해야 한다
 * (청사진 5장 "구현 시 반드시 지킬 것" 1번). 이 테스트는 H2 통과만 보장하며, 스키마 접두사 누락은
 * 운영에서만 터지는 리스크이므로 리포트에 별도로 남긴다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
class PlaceActivityRepositoryTest {

    @Autowired
    private PlaceActivityRepository placeActivityRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceReviewRepository placeReviewRepository;

    private static final Long MAP_ID = 10L;

    private Place.PlaceBuilder placeBuilder() {
        return Place.builder()
            .name("테스트 장소")
            .lat(java.math.BigDecimal.valueOf(37.5))
            .lng(java.math.BigDecimal.valueOf(127.0))
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(MAP_ID)
            .createdBy(1L)
            .status(PlaceStatus.APPROVED);
    }

    private PlaceReview.PlaceReviewBuilder reviewBuilder(Long placeId) {
        return PlaceReview.builder()
            .placeId(placeId)
            .userId(1L)
            .rating(5);
    }

    @Test
    void 세_이벤트가_섞여도_occurredAt_내림차순으로_정렬된다() {
        // given
        Place place = placeRepository.saveAndFlush(placeBuilder()
            .name("연남동 카페")
            .createdAt(LocalDateTime.of(2026, 7, 26, 9, 0))
            .build());
        placeReviewRepository.saveAndFlush(reviewBuilder(place.getId())
            .createdAt(LocalDateTime.of(2026, 7, 27, 14, 0))
            .build());
        Place deletedPlace = placeRepository.saveAndFlush(placeBuilder()
            .name("폐업한 국밥집")
            .createdAt(LocalDateTime.of(2026, 7, 25, 9, 0))
            .deletedAt(LocalDateTime.of(2026, 7, 27, 13, 0))
            .deletedBy(3L)
            .build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then: 리뷰 작성(14:00) > 장소 삭제(13:00) > 카페 추가(26일 9시) > 국밥집 추가(25일 9시)
        List<Object[]> rows = result.getContent();
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0)[0]).isEqualTo("REVIEW_CREATED");
        assertThat(rows.get(1)[0]).isEqualTo("PLACE_DELETED");
        assertThat(rows.get(2)[0]).isEqualTo("PLACE_ADDED");
        assertThat(rows.get(2)[4]).isEqualTo("연남동 카페");
        assertThat(rows.get(3)[0]).isEqualTo("PLACE_ADDED");
        assertThat(rows.get(3)[4]).isEqualTo("폐업한 국밥집");
    }

    @Test
    void includeReviews가_false면_리뷰_이벤트가_전혀_나오지_않는다() {
        // given: COMMUNITY 지도를 가정한 조회 (includeReviews=false)
        Place place = placeRepository.saveAndFlush(placeBuilder().build());
        placeReviewRepository.saveAndFlush(reviewBuilder(place.getId()).build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, false, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent())
            .extracting(row -> row[0])
            .doesNotContain("REVIEW_CREATED");
        assertThat(result.getContent()).hasSize(1); // PLACE_ADDED만
    }

    @Test
    void PENDING_상태_장소는_어떤_이벤트로도_나타나지_않는다() {
        // given
        placeRepository.saveAndFlush(placeBuilder().status(PlaceStatus.PENDING).build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void REJECTED_상태_장소는_어떤_이벤트로도_나타나지_않는다() {
        // given
        placeRepository.saveAndFlush(placeBuilder().status(PlaceStatus.REJECTED).build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void 소프트_삭제된_장소는_PLACE_ADDED와_PLACE_DELETED로는_나오지만_그_장소의_리뷰는_나오지_않는다() {
        // given
        Place place = placeRepository.saveAndFlush(placeBuilder()
            .deletedAt(LocalDateTime.of(2026, 7, 27, 10, 0))
            .deletedBy(9L)
            .build());
        placeReviewRepository.saveAndFlush(reviewBuilder(place.getId()).build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then: PLACE_ADDED + PLACE_DELETED 2건만, REVIEW_CREATED는 없음
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
            .extracting(row -> row[0])
            .containsExactlyInAnyOrder("PLACE_ADDED", "PLACE_DELETED");
    }

    @Test
    void 소프트_삭제된_리뷰는_노출되지_않는다() {
        // given
        Place place = placeRepository.saveAndFlush(placeBuilder().build());
        placeReviewRepository.saveAndFlush(reviewBuilder(place.getId())
            .deletedAt(LocalDateTime.of(2026, 7, 27, 10, 0))
            .build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then: PLACE_ADDED만 남는다
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)[0]).isEqualTo("PLACE_ADDED");
    }

    @Test
    void 추가_후_삭제된_장소는_2개의_이벤트로_나타난다() {
        // given
        placeRepository.saveAndFlush(placeBuilder()
            .createdAt(LocalDateTime.of(2026, 7, 20, 9, 0))
            .deletedAt(LocalDateTime.of(2026, 7, 27, 9, 0))
            .deletedBy(2L)
            .build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
            .extracting(row -> row[0])
            .containsExactlyInAnyOrder("PLACE_ADDED", "PLACE_DELETED");
    }

    @Test
    void deletedBy가_행에_그대로_담긴다() {
        // given
        placeRepository.saveAndFlush(placeBuilder()
            .deletedAt(LocalDateTime.of(2026, 7, 27, 9, 0))
            .deletedBy(42L)
            .build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then
        Object[] deletedRow = result.getContent().stream()
            .filter(row -> "PLACE_DELETED".equals(row[0]))
            .findFirst()
            .orElseThrow();
        assertThat(((Number) deletedRow[2]).longValue()).isEqualTo(42L);
    }

    @Test
    void deletedBy가_null인_과거_데이터는_actorId도_null로_나온다() {
        // given: deleted_by 컬럼이 없던 시절 데이터를 흉내
        placeRepository.saveAndFlush(placeBuilder()
            .deletedAt(LocalDateTime.of(2026, 7, 27, 9, 0))
            .deletedBy(null)
            .build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then
        Object[] deletedRow = result.getContent().stream()
            .filter(row -> "PLACE_DELETED".equals(row[0]))
            .findFirst()
            .orElseThrow();
        assertThat(deletedRow[2]).isNull();
    }

    @Test
    void offset과_limit이_정확히_동작한다() {
        // given: occurredAt이 서로 다른 5개의 PLACE_ADDED 이벤트
        for (int i = 0; i < 5; i++) {
            placeRepository.saveAndFlush(placeBuilder()
                .name("장소" + i)
                .createdAt(LocalDateTime.of(2026, 7, 20 + i, 9, 0))
                .build());
        }

        // when
        Page<Object[]> firstPage = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 2));
        Page<Object[]> secondPage = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(1, 2));
        Page<Object[]> thirdPage = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(2, 2));

        // then: 최신순이므로 첫 페이지는 장소4, 장소3
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent().get(0)[4]).isEqualTo("장소4");
        assertThat(firstPage.getContent().get(1)[4]).isEqualTo("장소3");
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent().get(0)[4]).isEqualTo("장소2");
        assertThat(secondPage.getContent().get(1)[4]).isEqualTo("장소1");
        assertThat(thirdPage.getContent()).hasSize(1);
        assertThat(thirdPage.getContent().get(0)[4]).isEqualTo("장소0");
        // 페이지 간 중복/누락이 없어야 한다
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(secondPage.getTotalElements()).isEqualTo(5);
    }

    @Test
    void totalElements는_세_소스의_필터_통과_행_수의_합과_같다() {
        // given: PLACE_ADDED 1 + PLACE_DELETED 1(다른 장소) + REVIEW_CREATED 1 = 3
        Place addedOnly = placeRepository.saveAndFlush(placeBuilder().name("장소A").build());
        placeRepository.saveAndFlush(placeBuilder()
            .name("장소B")
            .deletedAt(LocalDateTime.of(2026, 7, 27, 9, 0))
            .deletedBy(1L)
            .build());
        placeReviewRepository.saveAndFlush(reviewBuilder(addedOnly.getId()).build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then: 장소A(PLACE_ADDED) + 장소B(PLACE_ADDED, PLACE_DELETED) + 장소A 리뷰(REVIEW_CREATED) = 4
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getContent()).hasSize(4);
    }

    @Test
    void 동일한_occurredAt을_가진_행들도_결정적으로_정렬되어_페이지가_흔들리지_않는다() {
        // given: 같은 시각에 생성된 두 장소 (테스트의 일괄 insert에서 흔한 상황)
        LocalDateTime sameInstant = LocalDateTime.of(2026, 7, 27, 9, 0);
        placeRepository.saveAndFlush(placeBuilder().name("장소X").createdAt(sameInstant).build());
        placeRepository.saveAndFlush(placeBuilder().name("장소Y").createdAt(sameInstant).build());

        // when: 같은 조회를 두 번 반복
        Page<Object[]> first = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));
        Page<Object[]> second = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then: 두 번의 조회 결과 순서가 완전히 동일해야 한다 (place_id desc tie-break)
        assertThat(first.getContent().get(0)[3]).isEqualTo(second.getContent().get(0)[3]);
        assertThat(first.getContent().get(1)[3]).isEqualTo(second.getContent().get(1)[3]);
    }

    @Test
    void 다른_지도의_이벤트는_섞이지_않는다() {
        // given
        placeRepository.saveAndFlush(placeBuilder().mapId(MAP_ID).name("내_지도_장소").build());
        placeRepository.saveAndFlush(placeBuilder().mapId(999L).name("다른_지도_장소").build());

        // when
        Page<Object[]> result = placeActivityRepository.findActivities(MAP_ID, true, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)[4]).isEqualTo("내_지도_장소");
    }
}
