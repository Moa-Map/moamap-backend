package com.moamap.place.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;

public record PlaceResponse(
    Long id,
    String name,
    String address,
    String roadAddress,
    BigDecimal lat,
    BigDecimal lng,
    String category,
    String kakaoPlaceId,
    PlaceSourceType sourceType,
    String sourceUrl,
    String description,
    Long mapId,
    Long createdBy,
    PlaceStatus status,
    BigDecimal avgRating,
    Integer commentCount,
    Integer likeCount,
    boolean likedByMe,
    Long processedBy,
    LocalDateTime processedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<String> tags,
    List<String> photoUrls
) {

    /**
     * likedByMe는 "요청한 사람이 지금 하트를 눌러 둔 상태인지"다. 호출부가 반드시 실제 값을 넘긴다 —
     * 기본값 false를 주는 팩터리를 따로 두면 조회 경로에서 조용히 틀린 값이 나간다.
     */
    public static PlaceResponse of(Place place, boolean likedByMe) {
        return new PlaceResponse(
            place.getId(),
            place.getName(),
            place.getAddress(),
            place.getRoadAddress(),
            place.getLat(),
            place.getLng(),
            place.getCategory(),
            place.getKakaoPlaceId(),
            place.getSourceType(),
            place.getSourceUrl(),
            place.getDescription(),
            place.getMapId(),
            place.getCreatedBy(),
            place.getStatus(),
            place.getAvgRating(),
            place.getCommentCount(),
            place.getLikeCount(),
            likedByMe,
            place.getProcessedBy(),
            place.getProcessedAt(),
            place.getCreatedAt(),
            place.getUpdatedAt(),
            new ArrayList<>(place.getTags()),
            new ArrayList<>(place.getPhotoUrls())
        );
    }
}
