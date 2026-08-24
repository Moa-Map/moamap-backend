package com.moamap.place.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.repository.PlaceRepository;
import com.moamap.place.repository.PlaceCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceCommentService {

    private final PlaceCommentRepository placeCommentRepository;
    private final PlaceRepository placeRepository;
    private final MapClient mapClient;

    @Transactional
    public PlaceCommentResponse create(Long placeId, Long userId, PlaceCommentCreateRequest request) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Place place = getPlaceOrThrow(placeId);
        checkMapMember(place.getMapId(), userId);

        PlaceComment comment = PlaceComment.builder()
            .placeId(placeId)
            .userId(userId)
            .rating(request.rating())
            .content(request.content())
            .imageUrls(request.imageUrls() == null ? new ArrayList<>() : request.imageUrls())
            .build();
        placeCommentRepository.save(comment);
        refreshPlaceCommentSummary(placeId);
        return PlaceCommentResponse.from(comment);
    }

    public PageResponse<PlaceCommentResponse> findAllByPlaceId(Long placeId, Pageable pageable) {
        getPlaceOrThrow(placeId);
        return PageResponse.from(placeCommentRepository.findByPlaceIdAndDeletedAtIsNull(placeId, pageable)
            .map(PlaceCommentResponse::from));
    }

    @Transactional
    public PlaceCommentResponse update(Long placeId, Long commentId, Long userId, PlaceCommentUpdateRequest request) {
        PlaceComment comment = getCommentOrThrow(placeId, commentId);
        checkCommentOwner(comment, userId);
        comment.update(request.rating(), request.content(), request.imageUrls());
        refreshPlaceCommentSummary(placeId);
        return PlaceCommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long placeId, Long commentId, Long userId) {
        PlaceComment comment = getCommentOrThrow(placeId, commentId);
        checkCommentOwner(comment, userId);
        comment.delete();
        refreshPlaceCommentSummary(placeId);
    }

    /**
     * avgRating/commentCount는 댓글 테이블에서 매번 다시 계산하는 파생값이라 낙관적 락이 필요 없다.
     * 엔티티를 로드하지 않고 벌크 업데이트로 바로 반영해, Place.@Version과 충돌하지 않게 한다.
     */
    private void refreshPlaceCommentSummary(Long placeId) {
        long count = placeCommentRepository.countByPlaceIdAndDeletedAtIsNull(placeId);
        BigDecimal average = count == 0
            ? null
            : BigDecimal.valueOf(placeCommentRepository.averageRatingByPlaceId(placeId)).setScale(2, RoundingMode.HALF_UP);
        placeRepository.updateCommentSummary(placeId, average, (int) count);
    }

    private void checkMapMember(Long mapId, Long userId) {
        if (mapClient.getMemberInfo(mapId, userId).role() == MapMemberRole.NONE) {
            throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
        }
    }

    private void checkCommentOwner(PlaceComment comment, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(PlaceErrorCode.NOT_COMMENT_OWNER);
        }
    }

    private Place getPlaceOrThrow(Long placeId) {
        return placeRepository.findByIdAndDeletedAtIsNull(placeId)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private PlaceComment getCommentOrThrow(Long placeId, Long commentId) {
        PlaceComment comment = placeCommentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getPlaceId().equals(placeId)) {
            throw new BusinessException(PlaceErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }
}
