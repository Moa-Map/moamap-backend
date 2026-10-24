package com.moamap.place.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceComment;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

/**
 * 차단한 사용자의 장소·댓글을 빼는 조회가 실제 DB에서 맞게 동작하는지 확인한다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
class BlockedUserFilterQueryTest {

    private static final Long MAP_ID = 10L;
    private static final Long BLOCKED = 5L;
    private static final Long OTHER = 6L;

    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceCommentRepository placeCommentRepository;

    private int kakaoIdSeq;

    @Test
    void 차단한_사람이_등록한_장소를_빼고_페이지_수도_맞게_센다() {
        place(BLOCKED);
        Place visible = place(OTHER);

        var page = placeRepository.findByMapIdAndStatusAndDeletedAtIsNullAndCreatedByNotIn(
            MAP_ID, PlaceStatus.APPROVED, List.of(BLOCKED), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Place::getId).containsExactly(visible.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 차단한_사람의_댓글을_뺀다() {
        Place place = place(OTHER);
        comment(place, BLOCKED);
        PlaceComment visible = comment(place, OTHER);

        var page = placeCommentRepository.findByPlaceIdAndDeletedAtIsNullAndUserIdNotIn(
            place.getId(), List.of(BLOCKED), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(PlaceComment::getId).containsExactly(visible.getId());
    }

    private Place place(Long createdBy) {
        return placeRepository.saveAndFlush(Place.builder()
            .name("장소").lat(BigDecimal.valueOf(37.5)).lng(BigDecimal.valueOf(127.0))
            .kakaoPlaceId("B" + (++kakaoIdSeq)).sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(MAP_ID).createdBy(createdBy).status(PlaceStatus.APPROVED).build());
    }

    private PlaceComment comment(Place place, Long userId) {
        return placeCommentRepository.saveAndFlush(PlaceComment.builder()
            .placeId(place.getId()).userId(userId).rating(3).content("댓글").build());
    }
}
