package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceActivityResponse;
import com.moamap.place.service.PlaceActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places/activities")
@RequiredArgsConstructor
@Tag(name = "Place", description = "장소 등록/조회/수정/삭제 및 등록 승인·반려 API")
public class PlaceActivityController {

    private final PlaceActivityService placeActivityService;

    @GetMapping
    @Operation(
        summary = "지도 활동내역(로그) 조회",
        description = "지도의 장소 추가/삭제, 리뷰 작성 이력을 최신순으로 조회한다. 커뮤니티 지도는 로그인한 사용자면 "
            + "누구나, 프라이빗 지도는 해당 지도의 멤버만 조회할 수 있고, 공식 지도는 제공하지 않는다. 정렬은 항상 "
            + "발생 시각 내림차순으로 고정되며 sort 파라미터는 무시한다. size는 최대 100으로 제한된다."
    )
    public ApiResponse<PageResponse<PlaceActivityResponse>> getActivities(
        @Parameter(description = "조회할 지도 ID", required = true, example = "10")
        @RequestParam Long mapId,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        // size 상한 100은 UserClient 벌크 조회가 페이지당 항상 1회로 끝나기 위한 전제다.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100));
        return ApiResponse.success(placeActivityService.findByMapId(mapId, userId, unsorted));
    }
}
