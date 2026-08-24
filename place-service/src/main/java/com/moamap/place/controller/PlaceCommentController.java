package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.CommentPhotoUploadUrlRequest;
import com.moamap.place.dto.CommentPhotoUploadUrlResponse;
import com.moamap.place.dto.PlaceCommentCreateRequest;
import com.moamap.place.dto.PlaceCommentResponse;
import com.moamap.place.dto.PlaceCommentUpdateRequest;
import com.moamap.place.service.PlaceCommentPhotoService;
import com.moamap.place.service.PlaceCommentService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places/{placeId}/comments")
@RequiredArgsConstructor
@Tag(name = "PlaceComment", description = "장소 댓글 API (별점·사진 포함)")
public class PlaceCommentController {

    private final PlaceCommentService placeCommentService;
    private final PlaceCommentPhotoService placeCommentPhotoService;

    @PostMapping("/photo-upload-url")
    @Operation(
        summary = "댓글 이미지 업로드 URL 발급",
        description = """
            댓글 이미지를 올릴 presigned PUT URL을 발급한다. 이미지는 1장만 허용한다. 발급 권한은 댓글 작성 권한과 동일하다(해당 지도의 멤버).
            발급받은 uploadUrl로 파일을 직접 PUT한 뒤, 같은 응답의 fileUrl을 댓글 작성 요청의 imageUrls에 담아 전달한다.
            """
    )
    public ApiResponse<CommentPhotoUploadUrlResponse> photoUploadUrl(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @Valid @RequestBody CommentPhotoUploadUrlRequest request
    ) {
        return ApiResponse.success(placeCommentPhotoService.issueUploadUrl(placeId, request, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "댓글 작성", description = "해당 지도의 멤버만 작성할 수 있다. 한 사용자가 같은 장소에 여러 개 남길 수 있다.")
    public ApiResponse<PlaceCommentResponse> create(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody PlaceCommentCreateRequest request
    ) {
        return ApiResponse.success(placeCommentService.create(placeId, userId, request));
    }

    @GetMapping
    @Operation(summary = "댓글 목록 조회", description = "삭제되지 않은 댓글 목록을 페이지네이션으로 조회한다.")
    public ApiResponse<PageResponse<PlaceCommentResponse>> getAll(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(placeCommentService.findAllByPlaceId(placeId, pageable));
    }

    @PatchMapping("/{commentId}")
    @Operation(summary = "댓글 수정", description = "본인이 작성한 댓글만 수정할 수 있다.")
    public ApiResponse<PlaceCommentResponse> update(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(description = "댓글 ID", example = "1") @PathVariable Long commentId,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody PlaceCommentUpdateRequest request
    ) {
        return ApiResponse.success(placeCommentService.update(placeId, commentId, userId, request));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "댓글 삭제", description = "작성자 본인 또는 지도의 방장·관리자가 삭제할 수 있다. 실제로는 deletedAt만 채우는 소프트 삭제다.")
    public ApiResponse<Void> delete(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(description = "댓글 ID", example = "1") @PathVariable Long commentId,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId
    ) {
        placeCommentService.delete(placeId, commentId, userId);
        return ApiResponse.success();
    }
}
