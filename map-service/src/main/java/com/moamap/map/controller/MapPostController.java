package com.moamap.map.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.map.dto.MapPostCreateRequest;
import com.moamap.map.dto.MapPostPhotoUploadUrlRequest;
import com.moamap.map.dto.MapPostPhotoUploadUrlResponse;
import com.moamap.map.dto.MapPostResponse;
import com.moamap.map.dto.MapPostUpdateRequest;
import com.moamap.map.dto.PageResponse;
import com.moamap.map.service.MapPostPhotoService;
import com.moamap.map.service.MapPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MapPost", description = "지도 로그 탭 게시물 API")
@RestController
@RequestMapping("/api/v1/maps/{mapId}/posts")
@RequiredArgsConstructor
public class MapPostController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final MapPostService mapPostService;
    private final MapPostPhotoService mapPostPhotoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "게시물 작성",
        description = """
            로그 탭에 게시물을 작성한다. 지도 멤버만 작성할 수 있고, 커뮤니티 지도도 멤버여야 한다.
            placeTags는 태그 피커에서 고른 장소의 id와 이름을 그대로 담아 보낸다(서버는 검증하지 않는다).
            imageUrls는 사진 업로드 URL 발급 API로 올린 뒤 받은 fileUrl을 순서대로 담는다.
            """
    )
    public ApiResponse<MapPostResponse> create(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @Valid @RequestBody MapPostCreateRequest request,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(mapPostService.create(mapId, request, userId));
    }

    @GetMapping
    @Operation(
        summary = "게시물 목록 조회",
        description = "최신순으로 반환한다. 커뮤니티 지도는 누구나, 프라이빗 지도는 멤버만 조회할 수 있다."
    )
    public ApiResponse<PageResponse<MapPostResponse>> getAll(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(mapPostService.findAll(mapId, pageable, userId));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "게시물 상세 조회", description = "조회 권한은 목록과 같다.")
    public ApiResponse<MapPostResponse> getById(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @Parameter(description = "게시물 ID", example = "7") @PathVariable Long postId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(mapPostService.findById(mapId, postId, userId));
    }

    @PatchMapping("/{postId}")
    @Operation(
        summary = "게시물 수정",
        description = "작성자 본인만 수정할 수 있다. null인 필드는 기존 값을 유지하고, 컬렉션은 넘긴 값으로 전체 교체된다."
    )
    public ApiResponse<MapPostResponse> update(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @Parameter(description = "게시물 ID", example = "7") @PathVariable Long postId,
        @Valid @RequestBody MapPostUpdateRequest request,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(mapPostService.update(mapId, postId, request, userId));
    }

    @DeleteMapping("/{postId}")
    @Operation(
        summary = "게시물 삭제",
        description = "작성자 본인 또는 지도의 OWNER/ADMIN이 삭제할 수 있다(소프트 삭제). 댓글은 게시물과 함께 보이지 않게 된다."
    )
    public ApiResponse<Void> delete(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @Parameter(description = "게시물 ID", example = "7") @PathVariable Long postId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        mapPostService.delete(mapId, postId, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/photo-upload-url")
    @Operation(
        summary = "게시물 사진 업로드 URL 발급",
        description = """
            게시물 사진을 올릴 presigned PUT URL을 한 장씩 발급한다. 발급 권한은 게시물 작성 권한과 같다.
            허용 형식은 image/jpeg, image/png, image/webp이며 장당 최대 5MB다. 게시물에는 최대 5장까지 담을 수 있다.
            """
    )
    public ApiResponse<MapPostPhotoUploadUrlResponse> photoUploadUrl(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @Valid @RequestBody MapPostPhotoUploadUrlRequest request,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(mapPostPhotoService.issueUploadUrl(mapId, request, userId));
    }
}
