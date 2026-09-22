package com.moamap.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 하트를 누르거나 취소한 뒤의 상태.
 *
 * 요청이 무엇이었는지가 아니라 "지금 어떤 상태인지"를 준다. 같은 요청을 두 번 보내도 같은 답이 나오므로
 * 프론트는 응답을 그대로 화면에 반영하면 된다.
 */
@Schema(description = "장소 하트 상태")
public record PlaceLikeResponse(
    @Schema(description = "장소 ID", example = "1") Long placeId,
    @Schema(description = "하트를 누른 사람 수", example = "12") int likeCount,
    @Schema(description = "요청한 사용자가 지금 하트를 눌러 둔 상태인지", example = "true") boolean liked
) {

    public static PlaceLikeResponse of(Long placeId, long likeCount, boolean liked) {
        return new PlaceLikeResponse(placeId, (int) likeCount, liked);
    }
}
