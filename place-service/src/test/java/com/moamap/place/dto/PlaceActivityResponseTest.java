package com.moamap.place.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import com.moamap.place.entity.PlaceActivityType;
import org.junit.jupiter.api.Test;

/**
 * UNION ALL 네이티브 쿼리가 돌려주는 Object[] 행 -> PlaceActivityResponse 매핑을 검증한다.
 * 청사진 5장 "타임스탬프 매핑에 주의" 항목: Postgres 드라이버는 java.sql.Timestamp를,
 * H2는 LocalDateTime을 줄 수 있으므로 두 타입 모두 같은 결과로 변환돼야 한다.
 * 불변 조건 6/7(place류 이벤트는 reviewId/rating null, REVIEW_CREATED는 non-null)도 여기서 잡는다.
 */
class PlaceActivityResponseTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 27, 14, 2, 11);

    @Test
    void Timestamp로_들어온_occurredAt은_LocalDateTime으로_변환된다() {
        // given: Postgres 드라이버가 반환하는 형태
        Object[] row = {"PLACE_ADDED", Timestamp.valueOf(OCCURRED_AT), 7L, 31L, "연남동 카페", null, null};

        // when
        PlaceActivityResponse response = PlaceActivityResponse.from(row);

        // then
        assertThat(response.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void LocalDateTime으로_들어온_occurredAt은_그대로_사용된다() {
        // given: H2가 반환하는 형태
        Object[] row = {"PLACE_ADDED", OCCURRED_AT, 7L, 31L, "연남동 카페", null, null};

        // when
        PlaceActivityResponse response = PlaceActivityResponse.from(row);

        // then
        assertThat(response.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void PLACE_ADDED_행은_reviewId와_rating이_항상_null이다() {
        // given
        Object[] row = {"PLACE_ADDED", OCCURRED_AT, 7L, 31L, "연남동 카페", null, null};

        // when
        PlaceActivityResponse response = PlaceActivityResponse.from(row);

        // then
        assertThat(response.type()).isEqualTo(PlaceActivityType.PLACE_ADDED);
        assertThat(response.reviewId()).isNull();
        assertThat(response.rating()).isNull();
    }

    @Test
    void PLACE_DELETED_행은_reviewId와_rating이_항상_null이다() {
        // given
        Object[] row = {"PLACE_DELETED", OCCURRED_AT, 3L, 28L, "폐업한 국밥집", null, null};

        // when
        PlaceActivityResponse response = PlaceActivityResponse.from(row);

        // then
        assertThat(response.type()).isEqualTo(PlaceActivityType.PLACE_DELETED);
        assertThat(response.reviewId()).isNull();
        assertThat(response.rating()).isNull();
    }

    @Test
    void REVIEW_CREATED_행은_reviewId와_rating이_항상_non_null이다() {
        // given
        Object[] row = {"REVIEW_CREATED", OCCURRED_AT, 7L, 31L, "연남동 카페", 104L, 5};

        // when
        PlaceActivityResponse response = PlaceActivityResponse.from(row);

        // then
        assertThat(response.type()).isEqualTo(PlaceActivityType.REVIEW_CREATED);
        assertThat(response.reviewId()).isEqualTo(104L);
        assertThat(response.rating()).isEqualTo(5);
    }

    @Test
    void actorId가_null인_행도_예외_없이_null로_매핑된다() {
        // given: deleted_by 컬럼이 없던 시절 삭제된 장소
        Object[] row = {"PLACE_DELETED", OCCURRED_AT, null, 28L, "폐업한 국밥집", null, null};

        // when
        PlaceActivityResponse response = PlaceActivityResponse.from(row);

        // then
        assertThat(response.actorId()).isNull();
    }

    @Test
    void placeId와_placeName은_모든_이벤트_공통으로_채워진다() {
        // given
        Object[] row = {"PLACE_ADDED", OCCURRED_AT, 7L, 31L, "연남동 카페", null, null};

        // when
        PlaceActivityResponse response = PlaceActivityResponse.from(row);

        // then
        assertThat(response.placeId()).isEqualTo(31L);
        assertThat(response.placeName()).isEqualTo("연남동 카페");
    }

    /*
     * 델타: 06_architect_blueprint_nickname.md 2-3장 + 8장(열린 질문 해소 1).
     * from(Object[])이 만드는 시점에는 UNION 쿼리 결과에 닉네임/프로필이미지가 없으므로 항상 null이고,
     * PlaceActivityService가 UserClient 조회 결과로 나중에 withActorProfile()로 채워 넣는다.
     */

    @Test
    void from은_actorNickname과_actorProfileImageUrl을_항상_null로_생성한다() {
        // given
        Object[] row = {"PLACE_ADDED", OCCURRED_AT, 7L, 31L, "연남동 카페", null, null};

        // when
        PlaceActivityResponse response = PlaceActivityResponse.from(row);

        // then: UNION 쿼리 결과에는 닉네임/프로필이미지 컬럼이 없다
        assertThat(response.actorNickname()).isNull();
        assertThat(response.actorProfileImageUrl()).isNull();
    }

    @Test
    void withActorProfile은_닉네임과_프로필이미지만_바꾸고_나머지_필드는_보존한다() {
        // given
        Object[] row = {"REVIEW_CREATED", OCCURRED_AT, 7L, 31L, "연남동 카페", 104L, 5};
        PlaceActivityResponse original = PlaceActivityResponse.from(row);

        // when
        PlaceActivityResponse enriched = original.withActorProfile("민서", "http://img/profile.png");

        // then: 닉네임/프로필이미지만 채워지고 나머지는 그대로
        assertThat(enriched.actorNickname()).isEqualTo("민서");
        assertThat(enriched.actorProfileImageUrl()).isEqualTo("http://img/profile.png");
        assertThat(enriched.type()).isEqualTo(original.type());
        assertThat(enriched.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(enriched.actorId()).isEqualTo(original.actorId());
        assertThat(enriched.placeId()).isEqualTo(original.placeId());
        assertThat(enriched.placeName()).isEqualTo(original.placeName());
        assertThat(enriched.reviewId()).isEqualTo(original.reviewId());
        assertThat(enriched.rating()).isEqualTo(original.rating());
    }

    @Test
    void withActorProfile에_null을_넘기면_알_수_없음을_의미하는_null로_채워진다() {
        // given: 표 F - user-service 응답 배열에 없는 경우(없는 사용자/탈퇴자/호출 실패)
        Object[] row = {"PLACE_ADDED", OCCURRED_AT, 7L, 31L, "연남동 카페", null, null};
        PlaceActivityResponse original = PlaceActivityResponse.from(row);

        // when
        PlaceActivityResponse enriched = original.withActorProfile(null, null);

        // then
        assertThat(enriched.actorNickname()).isNull();
        assertThat(enriched.actorProfileImageUrl()).isNull();
    }
}
