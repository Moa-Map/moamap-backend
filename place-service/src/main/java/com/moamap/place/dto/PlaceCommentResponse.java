package com.moamap.place.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.moamap.place.entity.PlaceComment;

public record PlaceCommentResponse(
    Long id,
    Long placeId,
    Long userId,
    Integer rating,
    String content,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static PlaceCommentResponse from(PlaceComment comment) {
        return new PlaceCommentResponse(
            comment.getId(),
            comment.getPlaceId(),
            comment.getUserId(),
            comment.getRating(),
            comment.getContent(),
            new ArrayList<>(comment.getImageUrls()),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
