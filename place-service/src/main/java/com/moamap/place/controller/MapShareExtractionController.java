package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.MapShareExtractRequest;
import com.moamap.place.dto.MapShareExtractResponse;
import com.moamap.place.service.MapSharePlaceExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places/map-share-extractions")
@RequiredArgsConstructor
@Tag(name = "MapShareExtraction", description = "지도 공유 링크 장소 추출 API")
public class MapShareExtractionController {

    private final MapSharePlaceExtractionService mapSharePlaceExtractionService;

    @PostMapping
    @Operation(
        summary = "네이버·카카오·구글 지도 공유 리스트에서 장소 추출",
        description = """
            즐겨찾기 리스트 공유 링크에서 장소를 추출하고, 카카오 장소와 재매칭해 matched/unmatched로 나눠 반환한다.
            url에는 앱 공유로 들어온 문구가 섞인 텍스트를 그대로 넣어도 된다(서버가 첫 URL을 뽑아 쓴다).
            matched 항목에 mapId를 더하면 POST /api/v1/places/bulk 요청 항목이 된다.
            한 번에 최대 100개까지 처리하며, 초과 시 truncated=true로 알린다.
            """
    )
    public ApiResponse<MapShareExtractResponse> extract(
        @Valid @RequestBody MapShareExtractRequest request
    ) {
        return ApiResponse.success(mapSharePlaceExtractionService.extract(request));
    }
}
