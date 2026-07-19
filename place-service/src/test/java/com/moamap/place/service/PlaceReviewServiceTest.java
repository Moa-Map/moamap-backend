package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceReviewCreateRequest;
import com.moamap.place.dto.PlaceReviewResponse;
import com.moamap.place.dto.PlaceReviewUpdateRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceReview;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import com.moamap.place.repository.PlaceReviewRepository;
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
class PlaceReviewServiceTest {

    @Mock
    private PlaceReviewRepository placeReviewRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MapClient mapClient;

    @InjectMocks
    private PlaceReviewService placeReviewService;

    private Place place() {
        return Place.builder().name("스타벅스 강남점").mapId(10L).createdBy(1L).build();
    }

    private PlaceReviewCreateRequest createRequest() {
        return new PlaceReviewCreateRequest(5, "최고예요", List.of("https://img/1.jpg"));
    }

    @Test
    void create는_지도_멤버면_리뷰를_저장하고_Place_평균별점을_갱신한다() {
        // given
        Place place = place();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        given(placeReviewRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(1L);
        given(placeReviewRepository.averageRatingByPlaceId(any())).willReturn(new BigDecimal("5.00"));

        // when
        PlaceReviewResponse response = placeReviewService.create(1L, 2L, createRequest());

        // then
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.imageUrls()).containsExactly("https://img/1.jpg");
        verify(placeReviewRepository).save(any(PlaceReview.class));
        verify(placeRepository).updateReviewSummary(1L, new BigDecimal("5.00"), 1);
    }

    @Test
    void create는_지도_멤버가_아니면_BusinessException을_던진다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        // when & then
        assertThatThrownBy(() -> placeReviewService.create(1L, 2L, createRequest()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verify(placeReviewRepository, never()).save(any());
    }

    @Test
    void create는_imageUrls를_안_보내면_빈_리스트로_저장한다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(10L, 2L)).willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));
        given(placeReviewRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(1L);
        given(placeReviewRepository.averageRatingByPlaceId(any())).willReturn(new BigDecimal("5.00"));
        PlaceReviewCreateRequest request = new PlaceReviewCreateRequest(5, "최고예요", null);

        // when
        PlaceReviewResponse response = placeReviewService.create(1L, 2L, request);

        // then
        assertThat(response.imageUrls()).isEmpty();
    }

    @Test
    void create는_존재하지_않는_장소면_BusinessException을_던진다() {
        // given
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> placeReviewService.create(1L, 2L, createRequest()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
        verifyNoInteractions(mapClient);
    }

    @Test
    void create는_로그인하지_않았으면_BusinessException을_던진다() {
        // when & then
        assertThatThrownBy(() -> placeReviewService.create(1L, null, createRequest()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(placeRepository, mapClient, placeReviewRepository);
    }

    @Test
    void findAllByPlaceId는_페이지_결과를_반환한다() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        PlaceReview review = PlaceReview.builder().placeId(1L).userId(2L).rating(4).build();
        given(placeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(place()));
        given(placeReviewRepository.findByPlaceIdAndDeletedAtIsNull(1L, pageable))
            .willReturn(new PageImpl<>(List.of(review), pageable, 1));

        // when
        PageResponse<PlaceReviewResponse> result = placeReviewService.findAllByPlaceId(1L, pageable);

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
        assertThatThrownBy(() -> placeReviewService.findAllByPlaceId(1L, pageable))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
        verifyNoInteractions(placeReviewRepository);
    }

    @Test
    void update는_본인_리뷰면_수정하고_평균별점을_갱신한다() {
        // given
        PlaceReview review = PlaceReview.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeReviewRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(review));
        given(placeReviewRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(1L);
        given(placeReviewRepository.averageRatingByPlaceId(any())).willReturn(new BigDecimal("4.00"));
        PlaceReviewUpdateRequest request = new PlaceReviewUpdateRequest(4, "수정된 리뷰", null);

        // when
        PlaceReviewResponse response = placeReviewService.update(1L, 5L, 2L, request);

        // then
        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.content()).isEqualTo("수정된 리뷰");
        verify(placeRepository).updateReviewSummary(1L, new BigDecimal("4.00"), 1);
    }

    @Test
    void update는_본인_리뷰가_아니면_BusinessException을_던진다() {
        // given
        PlaceReview review = PlaceReview.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeReviewRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(review));
        PlaceReviewUpdateRequest request = new PlaceReviewUpdateRequest(4, "수정된 리뷰", null);

        // when & then
        assertThatThrownBy(() -> placeReviewService.update(1L, 5L, 3L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_REVIEW_OWNER);
    }

    @Test
    void update는_존재하지_않는_리뷰면_BusinessException을_던진다() {
        // given
        given(placeReviewRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.empty());
        PlaceReviewUpdateRequest request = new PlaceReviewUpdateRequest(4, "수정된 리뷰", null);

        // when & then
        assertThatThrownBy(() -> placeReviewService.update(1L, 5L, 2L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void delete는_본인_리뷰면_소프트_삭제하고_평균별점을_갱신한다() {
        // given
        PlaceReview review = PlaceReview.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeReviewRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(review));
        given(placeReviewRepository.countByPlaceIdAndDeletedAtIsNull(any())).willReturn(0L);

        // when
        placeReviewService.delete(1L, 5L, 2L);

        // then
        assertThat(review.getDeletedAt()).isNotNull();
        verify(placeReviewRepository, never()).averageRatingByPlaceId(any());
        verify(placeRepository).updateReviewSummary(1L, null, 0);
    }

    @Test
    void delete는_본인_리뷰가_아니면_BusinessException을_던진다() {
        // given
        PlaceReview review = PlaceReview.builder().placeId(1L).userId(2L).rating(3).build();
        given(placeReviewRepository.findByIdAndDeletedAtIsNull(5L)).willReturn(Optional.of(review));

        // when & then
        assertThatThrownBy(() -> placeReviewService.delete(1L, 5L, 3L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_REVIEW_OWNER);
        assertThat(review.getDeletedAt()).isNull();
    }
}
