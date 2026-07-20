package com.moamap.place.controller;

import java.util.List;
import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.InstagramExtractRequest;
import com.moamap.place.dto.PlaceCandidateResponse;
import com.moamap.place.service.PlaceExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places/instagram-extractions")
@RequiredArgsConstructor
@Tag(name = "PlaceExtraction", description = "AI 기반 인스타그램 장소 추출 API")
public class PlaceExtractionController {

    private final PlaceExtractionService placeExtractionService;

    @PostMapping
    @Operation(
        summary = "인스타그램 릴스 설명글에서 장소 후보 추출",
        description = "설명글에서 Gemini로 장소 단서를 추출해 카카오맵 검색 후보를 반환한다. 후보는 POST /places 등록에 그대로 사용할 수 있다."
    )
    public ApiResponse<List<PlaceCandidateResponse>> extract(
        @Valid @RequestBody InstagramExtractRequest request
    ) {
        return ApiResponse.success(placeExtractionService.extractFromInstagram(request));
    }
}
