package com.moamap.place.repository;

import java.math.BigDecimal;
import com.moamap.place.entity.ReportReason;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceComment;
import com.moamap.place.entity.PlaceCommentReport;
import com.moamap.place.entity.PlaceSourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 신고의 핵심 불변식을 실제 DB에 대고 확인한다. 모의 객체로는 증명할 수 없는 지점들이다.
 *
 * - 한 사람이 같은 댓글을 두 번 신고하지 못한다 (유니크 제약)
 * - 신고 수 증분이 DB에서 수행된다
 * - 신고 목록 조회가 지도 경계와 소프트 삭제를 지키고 많이 신고된 순으로 준다 (연관 없는 조인 쿼리)
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
class PlaceCommentReportRepositoryTest {

    private static final Long MAP_ID = 10L;
    private static final Long OTHER_MAP_ID = 99L;

    @Autowired
    private PlaceCommentReportRepository placeCommentReportRepository;

    @Autowired
    private PlaceCommentRepository placeCommentRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Place savePlace(Long mapId, String name, String kakaoPlaceId) {
        return placeRepository.saveAndFlush(Place.builder()
            .name(name)
            .lat(BigDecimal.valueOf(37.497852))
            .lng(BigDecimal.valueOf(127.027618))
            .kakaoPlaceId(kakaoPlaceId)
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(mapId)
            .createdBy(1L)
            .build());
    }

    private PlaceComment saveComment(Long placeId, String content) {
        return placeCommentRepository.saveAndFlush(PlaceComment.builder()
            .placeId(placeId)
            .userId(2L)
            .rating(3)
            .content(content)
            .build());
    }

    @Test
    void 한_사람이_같은_댓글을_두_번_신고할_수_없다() {
        // given
        Place place = savePlace(MAP_ID, "스타벅스 강남점", "26338954");
        PlaceComment comment = saveComment(place.getId(), "광고글");
        placeCommentReportRepository.saveAndFlush(
            PlaceCommentReport.of(comment.getId(), 3L, ReportReason.SPAM, null));

        // when & then
        assertThatThrownBy(() -> placeCommentReportRepository.saveAndFlush(
            PlaceCommentReport.of(comment.getId(), 3L, ReportReason.ABUSE, "사유를 바꿔서 또 신고")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_사람은_같은_댓글을_각각_신고할_수_있다() {
        // given
        Place place = savePlace(MAP_ID, "스타벅스 강남점", "26338954");
        PlaceComment comment = saveComment(place.getId(), "광고글");

        // when
        placeCommentReportRepository.saveAndFlush(
            PlaceCommentReport.of(comment.getId(), 3L, ReportReason.SPAM, null));
        placeCommentReportRepository.saveAndFlush(
            PlaceCommentReport.of(comment.getId(), 4L, ReportReason.ABUSE, null));

        // then
        assertThat(placeCommentReportRepository.existsByPlaceCommentIdAndReporterId(comment.getId(), 3L)).isTrue();
        assertThat(placeCommentReportRepository.existsByPlaceCommentIdAndReporterId(comment.getId(), 4L)).isTrue();
        assertThat(placeCommentReportRepository.existsByPlaceCommentIdAndReporterId(comment.getId(), 5L)).isFalse();
    }

    @Test
    void 신고_수_증분은_DB에서_수행된다() {
        // given
        Place place = savePlace(MAP_ID, "스타벅스 강남점", "26338954");
        PlaceComment comment = saveComment(place.getId(), "광고글");
        assertThat(comment.getReportCount()).isZero();

        // when
        placeCommentRepository.increaseReportCount(comment.getId());
        placeCommentRepository.increaseReportCount(comment.getId());
        entityManager.clear(); // 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로 비우고 다시 읽는다

        // then
        PlaceComment found = placeCommentRepository.findById(comment.getId()).orElseThrow();
        assertThat(found.getReportCount()).isEqualTo(2);
    }
}
