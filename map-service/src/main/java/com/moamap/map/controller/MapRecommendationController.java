package com.moamap.map.controller;

import java.util.List;
import com.moamap.common.response.ApiResponse;
import com.moamap.map.dto.MapRecommendationResponse;
import com.moamap.map.recommendation.MapRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MapRecommendation", description = "홈 화면 추천 지도 API")
@RestController
@RequestMapping("/api/v1/maps/recommendations")
@RequiredArgsConstructor
public class MapRecommendationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final MapRecommendationService recommendationService;

    @Operation(
        summary = "추천 커뮤니티 지도",
        description = "참여 중인 지도의 태그를 바탕으로 관심사가 비슷한 커뮤니티 지도를 추천한다. "
            + "참여 이력이 없거나 비로그인이면 인기·신선도 기준으로 채워 항상 결과를 반환한다."
    )
    @GetMapping
    public ApiResponse<List<MapRecommendationResponse>> recommend(
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(recommendationService.recommend(userId, size));
    }
}
