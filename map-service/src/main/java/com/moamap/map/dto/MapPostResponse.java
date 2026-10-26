package com.moamap.map.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.map.entity.MapPost;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그 탭 게시물")
public record MapPostResponse(
    @Schema(description = "게시물 ID", example = "1") Long id,
    @Schema(description = "지도 ID", example = "10") Long mapId,
    @Schema(description = "작성자 ID", example = "2") Long userId,
    @Schema(description = "본문") String content,
    @Schema(description = "사진 URL 목록") List<String> imageUrls,
    @Schema(description = "태그된 장소 목록") List<MapPostPlaceTagResponse> placeTags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static MapPostResponse from(MapPost post) {
        return new MapPostResponse(
            post.getId(), post.getMapId(), post.getUserId(), post.getContent(),
            List.copyOf(post.getImageUrls()),
            post.getPlaceTags().stream().map(MapPostPlaceTagResponse::from).toList(),
            post.getCreatedAt(), post.getUpdatedAt());
    }
}
