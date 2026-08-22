package com.moamap.map.dto;

import java.time.LocalDateTime;
import com.moamap.map.entity.MapPostComment;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그 탭 게시물 댓글")
public record MapPostCommentResponse(
    @Schema(description = "댓글 ID", example = "1") Long id,
    @Schema(description = "게시물 ID", example = "7") Long mapPostId,
    @Schema(description = "작성자 ID", example = "2") Long userId,
    @Schema(description = "댓글 내용") String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static MapPostCommentResponse from(MapPostComment comment) {
        return new MapPostCommentResponse(comment.getId(), comment.getMapPostId(), comment.getUserId(),
            comment.getContent(), comment.getCreatedAt(), comment.getUpdatedAt());
    }
}
