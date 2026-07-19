package com.moamap.place.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.moamap.place.entity.PlaceReview;

public record PlaceReviewResponse(
    Long id,
    Long placeId,
    Long userId,
    Integer rating,
    String content,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static PlaceReviewResponse from(PlaceReview review) {
        return new PlaceReviewResponse(
            review.getId(),
            review.getPlaceId(),
            review.getUserId(),
            review.getRating(),
            review.getContent(),
            new ArrayList<>(review.getImageUrls()),
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }
}
