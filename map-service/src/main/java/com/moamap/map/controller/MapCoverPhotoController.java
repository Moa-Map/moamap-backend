package com.moamap.map.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.map.dto.CoverUploadUrlRequest;
import com.moamap.map.dto.CoverUploadUrlResponse;
import com.moamap.map.service.MapCoverPhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MapCoverPhoto", description = "지도 커버 이미지 업로드 API")
@RestController
@RequestMapping("/api/v1/maps/cover-upload-url")
@RequiredArgsConstructor
public class MapCoverPhotoController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final MapCoverPhotoService mapCoverPhotoService;

    @Operation(
        summary = "지도 커버 이미지 업로드 URL 발급",
        description = """
            지도 커버 이미지를 올릴 presigned PUT URL을 발급한다. 지도를 만들기 전 커버를 고르는 화면에서 쓰므로 mapId를 받지 않는다.
            발급받은 uploadUrl로 파일을 직접 PUT한 뒤, 같은 응답의 fileUrl을 지도 생성/수정 요청의 imageUrl에 담아 전달한다.
            허용 형식은 image/jpeg, image/png, image/webp이며 최대 10MB까지 발급한다.
            """
    )
    @PostMapping
    public ApiResponse<CoverUploadUrlResponse> issueCoverUploadUrl(
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @Valid @RequestBody CoverUploadUrlRequest request
    ) {
        return ApiResponse.success(mapCoverPhotoService.issueUploadUrl(request, userId));
    }
}
