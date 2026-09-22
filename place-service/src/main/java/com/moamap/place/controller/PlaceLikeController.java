package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PlaceLikeResponse;
import com.moamap.place.service.PlaceLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places/{placeId}/likes")
@RequiredArgsConstructor
@Tag(name = "PlaceLike", description = "장소 하트 API")
public class PlaceLikeController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PlaceLikeService placeLikeService;

    @PostMapping
    @Operation(
        summary = "하트 누르기",
        description = """
            해당 지도의 멤버만 누를 수 있다. 이미 눌러 둔 상태에서 다시 호출해도 에러가 아니라
            현재 상태를 200으로 돌려준다 — 더블탭이나 재시도로 상태가 뒤집히지 않게 하기 위함이다.
            응답의 liked/likeCount를 그대로 화면에 반영하면 된다.
            """
    )
    public ApiResponse<PlaceLikeResponse> like(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(placeLikeService.like(placeId, userId));
    }

    @DeleteMapping
    @Operation(
        summary = "하트 취소",
        description = "누르지 않은 상태에서 호출해도 에러가 아니라 현재 상태를 200으로 돌려준다."
    )
    public ApiResponse<PlaceLikeResponse> unlike(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(placeLikeService.unlike(placeId, userId));
    }
}
