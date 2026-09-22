package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceCommentCreateRequest;
import com.moamap.place.dto.PlaceCommentResponse;
import com.moamap.place.dto.PlaceCommentUpdateRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceComment;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import com.moamap.place.repository.PlaceCommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlaceCommentServiceTest {

    @Mock
    private PlaceCommentRepository placeCommentRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MapClient mapClient;

    @InjectMocks
    private PlaceCommentService placeCommentService;

    private Place place() {
        return Place.builder().name("스타벅스 강남점").mapId(10L).createdBy(1L).build();
    }

    private PlaceCommentCreateRequest createRequest() {
        return new PlaceCommentCreateRequest(5, "최고예요", List.of("https://img/1.jpg"));
    }

    @Test
    void create는_지도_멤버면_댓글을_저장하고_Place_평균별점을_갱신한다() {
        // given
        Place place = place();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        given(placeCommentRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(1L);
        given(placeCommentRepository.averageRatingByPlaceId(any())).willReturn(5.0);

        // when
        PlaceCommentResponse response = placeCommentService.create(1L, 2L, createRequest());

        // then
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.imageUrls()).containsExactly("https://img/1.jpg");
        verify(placeCommentRepository).save(any(PlaceComment.class));
        verify(placeRepository).updateCommentSummary(1L, new BigDecimal("5.00"), 1);
    }

    @Test
    void create는_지도_멤버가_아니면_BusinessException을_던진다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        // when & then
        assertThatThrownBy(() -> placeCommentService.create(1L, 2L, createRequest()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verify(placeCommentRepository, never()).save(any());
    }

    @Test
    void create는_imageUrls를_안_보내면_빈_리스트로_저장한다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        given(placeCommentRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(1L);
        given(placeCommentRepository.averageRatingByPlaceId(any())).willReturn(5.0);
        PlaceCommentCreateRequest request = new PlaceCommentCreateRequest(5, "최고예요", null);

        // when
        PlaceCommentResponse response = placeCommentService.create(1L, 2L, request);

        // then
        assertThat(response.imageUrls()).isEmpty();
    }

    @Test
    void create는_존재하지_않는_장소면_BusinessException을_던진다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> placeCommentService.create(1L, 2L, createRequest()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
        verifyNoInteractions(mapClient);
    }

    @Test
    void create는_로그인하지_않았으면_BusinessException을_던진다() {
        // when & then
        assertThatThrownBy(() -> placeCommentService.create(1L, null, createRequest()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(placeRepository, mapClient, placeCommentRepository);
    }

    @Test
    void findAllByPlaceId는_페이지_결과를_반환한다() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(4).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(placeCommentRepository.findByPlaceIdAndDeletedAtIsNull(1L, pageable))
            .willReturn(new PageImpl<>(List.of(comment), pageable, 1));

        // when
        PageResponse<PlaceCommentResponse> result = placeCommentService.findAllByPlaceId(1L, pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).rating()).isEqualTo(4);
    }

    @Test
    void findAllByPlaceId는_존재하지_않는_장소면_BusinessException을_던진다() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> placeCommentService.findAllByPlaceId(1L, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
        verifyNoInteractions(placeCommentRepository);
    }

    @Test
    void update는_본인_댓글면_수정하고_평균별점을_갱신한다() {
        // given
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(comment));
        given(placeCommentRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(1L);
        given(placeCommentRepository.averageRatingByPlaceId(any())).willReturn(4.0);
        PlaceCommentUpdateRequest request = new PlaceCommentUpdateRequest(4, "수정된 댓글", null);

        // when
        PlaceCommentResponse response = placeCommentService.update(1L, 5L, 2L, request);

        // then
        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.content()).isEqualTo("수정된 댓글");
        verify(placeRepository).updateCommentSummary(1L, new BigDecimal("4.00"), 1);
    }

    @Test
    void update는_본인_댓글이_아니면_BusinessException을_던진다() {
        // given
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(comment));
        PlaceCommentUpdateRequest request = new PlaceCommentUpdateRequest(4, "수정된 댓글", null);

        // when & then
        assertThatThrownBy(() -> placeCommentService.update(1L, 5L, 3L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_COMMENT_OWNER);
    }

    @Test
    void update는_존재하지_않는_댓글면_BusinessException을_던진다() {
        // given
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.empty());
        PlaceCommentUpdateRequest request = new PlaceCommentUpdateRequest(4, "수정된 댓글", null);

        // when & then
        assertThatThrownBy(() -> placeCommentService.update(1L, 5L, 2L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void delete는_본인_댓글면_소프트_삭제하고_평균별점을_갱신한다() {
        // given
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(comment));
        given(placeCommentRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(0L);

        // when
        placeCommentService.delete(1L, 5L, 2L);

        // then
        assertThat(comment.getDeletedAt()).isNotNull();
        verify(placeCommentRepository, never()).averageRatingByPlaceId(any());
        verify(placeRepository).updateCommentSummary(1L, null, 0);
    }

    @Test
    void delete는_본인_댓글이_아니고_관리_역할도_아니면_BusinessException을_던진다() {
        // given
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(comment));
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(10L, 3L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placeCommentService.delete(1L, 5L, 3L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_COMMENT_OWNER);
        assertThat(comment.getDeletedAt()).isNull();
    }

    @Test
    void delete는_남의_댓글이어도_지도_OWNER면_삭제할_수_있다() {
        // given
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(comment));
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(10L, 3L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.OWNER));
        given(placeCommentRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(0L);

        // when
        placeCommentService.delete(1L, 5L, 3L);

        // then
        assertThat(comment.getDeletedAt()).isNotNull();
        verify(placeRepository).updateCommentSummary(1L, null, 0);
    }

    @Test
    void delete는_남의_댓글이어도_지도_ADMIN이면_삭제할_수_있다() {
        // given
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(comment));
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(10L, 3L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.ADMIN));
        given(placeCommentRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(0L);

        // when
        placeCommentService.delete(1L, 5L, 3L);

        // then
        assertThat(comment.getDeletedAt()).isNotNull();
    }

    /** 본인 삭제는 지도 왕복이 필요 없다. 서비스 간 호출을 아끼는 설계라 회귀하지 않게 고정한다. */
    @Test
    void delete는_본인_댓글이면_map_service를_호출하지_않는다() {
        // given
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(comment));
        given(placeCommentRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(0L);

        // when
        placeCommentService.delete(1L, 5L, 2L);

        // then
        verifyNoInteractions(mapClient);
    }

    /** 수정 권한은 확장하지 않는다 — 남의 글 내용을 고치는 건 관리 행위가 아니다. */
    @Test
    void update는_지도_OWNER여도_작성자가_아니면_거부된다() {
        // given
        PlaceComment comment = PlaceComment.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(comment));
        PlaceCommentUpdateRequest request = new PlaceCommentUpdateRequest(4, "관리자가 고친 내용", null);

        // when & then
        assertThatThrownBy(() -> placeCommentService.update(1L, 5L, 3L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_COMMENT_OWNER);
        verifyNoInteractions(mapClient);
    }
}
