package com.moamap.place.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceReviewCreateRequest;
import com.moamap.place.dto.PlaceReviewResponse;
import com.moamap.place.dto.PlaceReviewUpdateRequest;
import com.moamap.place.dto.ReviewPhotoUploadUrlRequest;
import com.moamap.place.dto.ReviewPhotoUploadUrlResponse;
import com.moamap.place.service.PlaceReviewPhotoService;
import com.moamap.place.service.PlaceReviewService;
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
@RequestMapping("/api/v1/places/{placeId}/reviews")
@RequiredArgsConstructor
@Tag(name = "PlaceReview", description = "장소 별점/댓글/사진 리뷰 API")
public class PlaceReviewController {

    private final PlaceReviewService placeReviewService;
    private final PlaceReviewPhotoService placeReviewPhotoService;

    @PostMapping("/photo-upload-url")
    @Operation(
        summary = "리뷰 이미지 업로드 URL 발급",
        description = """
            리뷰 이미지를 올릴 presigned PUT URL을 발급한다. 이미지는 1장만 허용한다. 발급 권한은 리뷰 작성 권한과 동일하다(해당 지도의 멤버).
            발급받은 uploadUrl로 파일을 직접 PUT한 뒤, 같은 응답의 fileUrl을 리뷰 작성 요청의 imageUrls에 담아 전달한다.
            """
    )
    public ApiResponse<ReviewPhotoUploadUrlResponse> photoUploadUrl(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @Valid @RequestBody ReviewPhotoUploadUrlRequest request
    ) {
        return ApiResponse.success(placeReviewPhotoService.issueUploadUrl(placeId, request, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "리뷰 작성", description = "해당 지도의 멤버(또는 팔로워)만 작성할 수 있다. 한 사용자가 같은 장소에 여러 개 남길 수 있다.")
    public ApiResponse<PlaceReviewResponse> create(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody PlaceReviewCreateRequest request
    ) {
        return ApiResponse.success(placeReviewService.create(placeId, userId, request));
    }

    @GetMapping
    @Operation(summary = "리뷰 목록 조회", description = "삭제되지 않은 리뷰 목록을 페이지네이션으로 조회한다.")
    public ApiResponse<PageResponse<PlaceReviewResponse>> getAll(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(placeReviewService.findAllByPlaceId(placeId, pageable));
    }

    @PatchMapping("/{reviewId}")
    @Operation(summary = "리뷰 수정", description = "본인이 작성한 리뷰만 수정할 수 있다.")
    public ApiResponse<PlaceReviewResponse> update(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(description = "리뷰 ID", example = "1") @PathVariable Long reviewId,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody PlaceReviewUpdateRequest request
    ) {
        return ApiResponse.success(placeReviewService.update(placeId, reviewId, userId, request));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "리뷰 삭제", description = "본인이 작성한 리뷰만 삭제할 수 있다. 실제로는 deletedAt만 채우는 소프트 삭제다.")
    public ApiResponse<Void> delete(
        @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
        @Parameter(description = "리뷰 ID", example = "1") @PathVariable Long reviewId,
        @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId
    ) {
        placeReviewService.delete(placeId, reviewId, userId);
        return ApiResponse.success();
    }
}
