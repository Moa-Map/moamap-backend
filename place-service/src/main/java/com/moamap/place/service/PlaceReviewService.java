package com.moamap.place.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.repository.PlaceRepository;
import com.moamap.place.repository.PlaceReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceReviewService {

    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceRepository placeRepository;
    private final MapClient mapClient;

    @Transactional
    public PlaceReviewResponse create(Long placeId, Long userId, PlaceReviewCreateRequest request) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Place place = getPlaceOrThrow(placeId);
        checkMapMember(place.getMapId(), userId);

        PlaceReview review = PlaceReview.builder()
            .placeId(placeId)
            .userId(userId)
            .rating(request.rating())
            .content(request.content())
            .imageUrls(request.imageUrls() == null ? List.of() : request.imageUrls())
            .build();
        placeReviewRepository.save(review);
        refreshPlaceReviewSummary(placeId);
        return PlaceReviewResponse.from(review);
    }

    public PageResponse<PlaceReviewResponse> findAllByPlaceId(Long placeId, Pageable pageable) {
        getPlaceOrThrow(placeId);
        return PageResponse.from(placeReviewRepository.findByPlaceIdAndDeletedAtIsNull(placeId, pageable)
            .map(PlaceReviewResponse::from));
    }

    @Transactional
    public PlaceReviewResponse update(Long placeId, Long reviewId, Long userId, PlaceReviewUpdateRequest request) {
        PlaceReview review = getReviewOrThrow(placeId, reviewId);
        checkReviewOwner(review, userId);
        review.update(request.rating(), request.content(), request.imageUrls());
        refreshPlaceReviewSummary(placeId);
        return PlaceReviewResponse.from(review);
    }

    @Transactional
    public void delete(Long placeId, Long reviewId, Long userId) {
        PlaceReview review = getReviewOrThrow(placeId, reviewId);
        checkReviewOwner(review, userId);
        review.delete();
        refreshPlaceReviewSummary(placeId);
    }

    /**
     * avgRating/commentCount는 리뷰 테이블에서 매번 다시 계산하는 파생값이라 낙관적 락이 필요 없다.
     * 엔티티를 로드하지 않고 벌크 업데이트로 바로 반영해, Place.@Version과 충돌하지 않게 한다.
     */
    private void refreshPlaceReviewSummary(Long placeId) {
        long count = placeReviewRepository.countByPlaceIdAndDeletedAtIsNull(placeId);
        BigDecimal average = count == 0
            ? null
            : placeReviewRepository.averageRatingByPlaceId(placeId).setScale(2, RoundingMode.HALF_UP);
        placeRepository.updateReviewSummary(placeId, average, (int) count);
    }

    private void checkMapMember(Long mapId, Long userId) {
        if (mapClient.getMemberInfo(mapId, userId).role() == MapMemberRole.NONE) {
            throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
        }
    }

    private void checkReviewOwner(PlaceReview review, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(PlaceErrorCode.NOT_REVIEW_OWNER);
        }
    }

    private Place getPlaceOrThrow(Long placeId) {
        return placeRepository.findByIdAndDeletedAtIsNull(placeId)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private PlaceReview getReviewOrThrow(Long placeId, Long reviewId) {
        PlaceReview review = placeReviewRepository.findByIdAndDeletedAtIsNull(reviewId)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.REVIEW_NOT_FOUND));
        if (!review.getPlaceId().equals(placeId)) {
            throw new BusinessException(PlaceErrorCode.REVIEW_NOT_FOUND);
        }
        return review;
    }
}
