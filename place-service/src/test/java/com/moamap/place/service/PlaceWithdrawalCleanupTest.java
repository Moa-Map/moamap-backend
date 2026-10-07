package com.moamap.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import com.moamap.place.entity.CommentReportReason;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceComment;
import com.moamap.place.entity.PlaceCommentReport;
import com.moamap.place.entity.PlaceLike;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.map.MapClient;
import com.moamap.place.repository.PlaceCommentReportRepository;
import com.moamap.place.repository.PlaceCommentRepository;
import com.moamap.place.repository.PlaceLikeRepository;
import com.moamap.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 탈퇴 회원의 하트·댓글 정리. 지운 뒤 장소의 하트 수·댓글 수·평점이 남은 데이터 기준으로 맞는지 실제 DB로 확인한다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
@Import({PlaceWithdrawalCleanupService.class, PlaceCommentService.class})
class PlaceWithdrawalCleanupTest {

    private static final Long LEAVER = 10L;
    private static final Long OTHER = 20L;

    @Autowired private PlaceWithdrawalCleanupService cleanupService;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceLikeRepository placeLikeRepository;
    @Autowired private PlaceCommentRepository placeCommentRepository;
    @Autowired private PlaceCommentReportRepository placeCommentReportRepository;
    @Autowired private TestEntityManager em;
    @MockitoBean private MapClient mapClient;

    private int kakaoPlaceSeq;

    @Test
    void 하트를_지우고_하트_수를_남은_하트로_다시_센다() {
        Place place = place();
        like(place, LEAVER);
        like(place, OTHER);

        cleanUpAndReload(LEAVER);

        assertThat(placeLikeRepository.existsByPlaceIdAndUserId(place.getId(), LEAVER)).isFalse();
        assertThat(placeLikeRepository.existsByPlaceIdAndUserId(place.getId(), OTHER)).isTrue();
        assertThat(placeRepository.findById(place.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
    }

    @Test
    void 댓글을_지우고_댓글_수와_평점을_남은_댓글로_다시_계산한다() {
        Place place = place();
        PlaceComment mine = comment(place, LEAVER, 1);
        PlaceComment others = comment(place, OTHER, 5);

        cleanUpAndReload(LEAVER);

        assertThat(placeCommentRepository.findById(mine.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(placeCommentRepository.findById(others.getId()).orElseThrow().getDeletedAt()).isNull();
        Place reloaded = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(reloaded.getCommentCount()).isEqualTo(1);
        assertThat(reloaded.getAvgRating()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void 남은_댓글이_없으면_평점은_비운다() {
        Place place = place();
        comment(place, LEAVER, 4);

        cleanUpAndReload(LEAVER);

        Place reloaded = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(reloaded.getCommentCount()).isZero();
        assertThat(reloaded.getAvgRating()).isNull();
    }

    @Test
    void 등록한_장소와_신고_기록은_남긴다() {
        Place myPlace = place(LEAVER);
        PlaceComment othersComment = comment(myPlace, OTHER, 3);
        PlaceCommentReport report = placeCommentReportRepository.saveAndFlush(
            PlaceCommentReport.of(othersComment.getId(), LEAVER, CommentReportReason.SPAM, null));

        cleanUpAndReload(LEAVER);

        assertThat(placeRepository.findById(myPlace.getId())).isPresent();
        assertThat(placeCommentReportRepository.findById(report.getId())).isPresent();
    }

    @Test
    void 같은_이벤트가_다시_와도_결과가_같다() {
        Place place = place();
        like(place, LEAVER);
        comment(place, LEAVER, 2);
        cleanUpAndReload(LEAVER);

        assertThatCode(() -> cleanUpAndReload(LEAVER)).doesNotThrowAnyException();

        Place reloaded = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(reloaded.getLikeCount()).isZero();
        assertThat(reloaded.getCommentCount()).isZero();
    }

    private void cleanUpAndReload(Long userId) {
        cleanupService.cleanUp(userId);
        em.flush();
        em.clear();
    }

    private Place place() {
        return place(OTHER);
    }

    private Place place(Long createdBy) {
        return placeRepository.saveAndFlush(Place.builder()
            .name("장소")
            .lat(BigDecimal.valueOf(37.497852))
            .lng(BigDecimal.valueOf(127.027618))
            .kakaoPlaceId(String.valueOf(++kakaoPlaceSeq))
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(1L)
            .createdBy(createdBy)
            .build());
    }

    private void like(Place place, Long userId) {
        placeLikeRepository.saveAndFlush(PlaceLike.of(place.getId(), userId));
    }

    private PlaceComment comment(Place place, Long userId, int rating) {
        return placeCommentRepository.saveAndFlush(PlaceComment.builder()
            .placeId(place.getId())
            .userId(userId)
            .rating(rating)
            .content("댓글")
            .build());
    }
}
