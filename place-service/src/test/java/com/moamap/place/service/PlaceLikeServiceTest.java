package com.moamap.place.service;

import java.sql.SQLException;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PlaceLikeResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 권한 분기와 유니크 제약 위반 처리를 검증한다.
 * 멱등성·동시성처럼 실제 DB가 있어야 증명되는 것은 PlaceLikeIntegrationTest에서 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceLikeServiceTest {

    private static final Long PLACE_ID = 1L;
    private static final Long MAP_ID = 10L;
    private static final Long USER_ID = 2L;

    @Mock
    private PlaceLikeWriter placeLikeWriter;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MapClient mapClient;

    @InjectMocks
    private PlaceLikeService placeLikeService;

    private Place place() {
        return Place.builder().name("스타벅스 강남점").mapId(MAP_ID).createdBy(1L).build();
    }

    private void givenMember(MapMemberRole role) {
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, USER_ID)).willReturn(new MapMemberResponse(MapType.COMMUNITY, role));
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException("제약 위반",
            new ConstraintViolationException("제약 위반", new SQLException(), constraintName));
    }

    @Test
    void 멤버는_하트를_누를_수_있다() {
        // given
        givenMember(MapMemberRole.MEMBER);
        given(placeLikeWriter.likeIfAbsent(PLACE_ID, USER_ID)).willReturn(true);
        given(placeLikeWriter.refreshLikeCount(PLACE_ID)).willReturn(3L);

        // when
        PlaceLikeResponse response = placeLikeService.like(PLACE_ID, USER_ID);

        // then
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(3);
        verify(placeLikeWriter).likeIfAbsent(PLACE_ID, USER_ID);
    }

    @Test
    void 멤버는_하트를_취소할_수_있다() {
        // given
        givenMember(MapMemberRole.MEMBER);
        given(placeLikeWriter.unlikeIfPresent(PLACE_ID, USER_ID)).willReturn(true);
        given(placeLikeWriter.refreshLikeCount(PLACE_ID)).willReturn(0L);

        // when
        PlaceLikeResponse response = placeLikeService.unlike(PLACE_ID, USER_ID);

        // then
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isZero();
        verify(placeLikeWriter).unlikeIfPresent(PLACE_ID, USER_ID);
    }

    /** 동시에 겹친 요청이 유니크 제약에 걸려도, 목표 상태는 달성됐으므로 에러가 아니라 현재 상태로 답한다. */
    @Test
    void 동시_요청으로_유니크_제약에_걸려도_에러가_아니라_현재_상태를_준다() {
        // given
        givenMember(MapMemberRole.MEMBER);
        willThrow(constraintViolation("uk_place_likes_place_user"))
            .given(placeLikeWriter).likeIfAbsent(PLACE_ID, USER_ID);
        given(placeLikeWriter.currentLikeCount(PLACE_ID)).willReturn(1L);

        // when
        PlaceLikeResponse response = placeLikeService.like(PLACE_ID, USER_ID);

        // then
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(1);
    }

    /** 하트 중복이 아닌 무결성 위반까지 "이미 눌림"으로 뭉개면 진짜 오류를 놓친다. */
    @Test
    void 다른_무결성_위반은_그대로_전파된다() {
        // given
        givenMember(MapMemberRole.MEMBER);
        willThrow(constraintViolation("uk_something_else"))
            .given(placeLikeWriter).likeIfAbsent(PLACE_ID, USER_ID);

        // when & then
        assertThatThrownBy(() -> placeLikeService.like(PLACE_ID, USER_ID))
            .isInstanceOf(DataIntegrityViolationException.class);
        verify(placeLikeWriter, org.mockito.Mockito.never()).refreshLikeCount(any());
    }

    @Test
    void 비로그인은_하트를_누를_수_없다() {
        // when & then
        assertThatThrownBy(() -> placeLikeService.like(PLACE_ID, null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(placeLikeWriter);
    }

    @Test
    void 비멤버는_하트를_누를_수_없다() {
        // given
        givenMember(MapMemberRole.NONE);

        // when & then
        assertThatThrownBy(() -> placeLikeService.like(PLACE_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verifyNoInteractions(placeLikeWriter);
    }

    @Test
    void 없는_장소에는_하트를_누를_수_없다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> placeLikeService.like(PLACE_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
        verifyNoInteractions(placeLikeWriter);
    }

    /** 취소도 쓰기다. 권한 검사를 빠뜨리면 비멤버가 남의 지도 카운트를 흔들 수 있다. */
    @Test
    void 취소도_비멤버는_할_수_없다() {
        // given
        givenMember(MapMemberRole.NONE);

        // when & then
        assertThatThrownBy(() -> placeLikeService.unlike(PLACE_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verifyNoInteractions(placeLikeWriter);
    }

    /** 더블탭처럼 바뀐 게 없는 요청은 places를 UPDATE하지 않는다 — 인기 장소에서 쓰기 경합만 만든다. */
    @Test
    void 이미_눌러_둔_상태면_카운트를_다시_쓰지_않고_읽기만_한다() {
        // given
        givenMember(MapMemberRole.MEMBER);
        given(placeLikeWriter.likeIfAbsent(PLACE_ID, USER_ID)).willReturn(false);
        given(placeLikeWriter.currentLikeCount(PLACE_ID)).willReturn(7L);

        // when
        PlaceLikeResponse response = placeLikeService.like(PLACE_ID, USER_ID);

        // then
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(7);
        verify(placeLikeWriter, org.mockito.Mockito.never()).refreshLikeCount(any());
    }

    @Test
    void 누른_적_없는데_취소하면_카운트를_다시_쓰지_않는다() {
        // given
        givenMember(MapMemberRole.MEMBER);
        given(placeLikeWriter.unlikeIfPresent(PLACE_ID, USER_ID)).willReturn(false);
        given(placeLikeWriter.currentLikeCount(PLACE_ID)).willReturn(7L);

        // when
        PlaceLikeResponse response = placeLikeService.unlike(PLACE_ID, USER_ID);

        // then
        assertThat(response.liked()).isFalse();
        verify(placeLikeWriter, org.mockito.Mockito.never()).refreshLikeCount(any());
    }
}
