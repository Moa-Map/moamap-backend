package com.moamap.place.controller;

import java.util.List;
import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PhotoUploadUrlRequest;
import com.moamap.place.dto.PhotoUploadUrlResponse;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.dto.PlaceResponse;
import com.moamap.place.dto.PlaceUpdateRequest;
import com.moamap.place.service.PlacePhotoService;
import com.moamap.place.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Tag(name = "Place", description = "장소 등록/조회/수정/삭제 및 등록 승인·반려 API")
public class PlaceController {

    private final PlaceService placeService;
    private final PlacePhotoService placePhotoService;

    @PostMapping("/photo-upload-url")
    @Operation(
        summary = "장소 사진 업로드 presigned URL 최대 5장 일괄 발급",
        description = """
            장소 사진 업로드용 presigned PUT URL을 한 요청에 최대 5장 일괄 발급한다. 발급 권한은 장소 등록 권한과 동일하다(OFFICIAL 지도 불가, 지도 멤버여야 함).
            발급받은 각 uploadUrl로 클라이언트가 직접 PUT 업로드한 뒤, fileUrl 목록을 장소 등록 요청의 photoUrls에 담아 전달한다.
            """
    )
    public ApiResponse<List<PhotoUploadUrlResponse>> photoUploadUrl(
        @Valid @RequestBody PhotoUploadUrlRequest request,
        @Parameter(description = "요청자 사용자 ID", required = true, example = "1")
        @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.success(placePhotoService.issueUploadUrls(request, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "장소 등록",
        description = """
            지도에 장소를 등록한다. 지도 내 역할에 따라 상태가 결정된다: PRIVATE 멤버·COMMUNITY OWNER/ADMIN은 즉시 APPROVED,
            COMMUNITY MEMBER는 PENDING(승인 대기). OFFICIAL 지도이거나 멤버가 아니면 403.
            인스타그램 추출 후보(PlaceCandidateResponse)를 등록할 때는 후보 값에 sourceType("INSTAGRAM")과 mapId를 추가로 채운다.
            """
    )
    public ApiResponse<PlaceResponse> create(
        @Valid @RequestBody PlaceCreateRequest request,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.success(placeService.create(request, userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "장소 단건 조회", description = "삭제되지 않은 장소를 id로 조회한다.")
    public ApiResponse<PlaceResponse> getById(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long id
    ) {
        return ApiResponse.success(placeService.findById(id));
    }

    @GetMapping
    @Operation(
        summary = "지도별 장소 목록 조회",
        description = "특정 지도에 속한, 삭제되지 않은 장소 중 APPROVED 상태만 조회한다. PENDING 상태는 별도의 승인 대기 목록 API에서 확인한다."
    )
    public ApiResponse<PageResponse<PlaceResponse>> getAll(
        @Parameter(description = "조회할 지도 ID", required = true, example = "10")
        @RequestParam Long mapId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(placeService.findAllByMapId(mapId, pageable));
    }

    @GetMapping("/pending")
    @Operation(
        summary = "승인 대기 장소 목록 조회",
        description = "특정 지도의 PENDING 상태 장소 목록을 조회한다. 해당 지도의 OWNER/ADMIN만 호출할 수 있다."
    )
    public ApiResponse<PageResponse<PlaceResponse>> getPending(
        @Parameter(description = "조회할 지도 ID", required = true, example = "10")
        @RequestParam Long mapId,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(placeService.findPendingByMapId(mapId, userId, pageable));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "장소 정보 수정", description = "장소를 등록한 본인만 이름/주소/좌표/카테고리/설명을 수정할 수 있다.")
    public ApiResponse<PlaceResponse> update(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long id,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody PlaceUpdateRequest request
    ) {
        return ApiResponse.success(placeService.update(id, userId, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "장소 삭제", description = "장소를 등록한 본인만 삭제할 수 있다. 실제로는 deletedAt만 채우는 소프트 삭제다.")
    public void delete(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long id,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId
    ) {
        placeService.delete(id, userId);
    }

    @PatchMapping("/{id}/approve")
    @Operation(
        summary = "장소 등록 승인",
        description = "PENDING 상태인 장소를 승인한다. 해당 지도의 OWNER/ADMIN만 호출할 수 있다."
    )
    public ApiResponse<PlaceResponse> approve(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long id,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.success(placeService.approve(id, userId));
    }

    @PatchMapping("/{id}/reject")
    @Operation(
        summary = "장소 등록 반려",
        description = "PENDING 상태인 장소를 반려한다. 해당 지도의 OWNER/ADMIN만 호출할 수 있다."
    )
    public ApiResponse<PlaceResponse> reject(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long id,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.success(placeService.reject(id, userId));
    }
}
