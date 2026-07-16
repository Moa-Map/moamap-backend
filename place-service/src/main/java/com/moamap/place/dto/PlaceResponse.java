package com.moamap.place.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    Long processedBy,
    LocalDateTime processedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static PlaceResponse from(Place place) {
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
            place.getProcessedBy(),
            place.getProcessedAt(),
            place.getCreatedAt(),
            place.getUpdatedAt()
        );
    }
}
