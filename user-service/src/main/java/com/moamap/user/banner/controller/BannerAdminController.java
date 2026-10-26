package com.moamap.user.banner.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.banner.dto.AdminBannerResponse;
import com.moamap.user.banner.dto.BannerImageRequest;
import com.moamap.user.banner.dto.BannerImageUploadUrlRequest;
import com.moamap.user.banner.dto.BannerImageUploadUrlResponse;
import com.moamap.user.banner.dto.BannerOrderUpdateRequest;
import com.moamap.user.banner.service.BannerAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용. 권한은 서비스가 회원 역할(Role.ADMIN)로 확인한다.
 */
@Tag(name = "Banner Admin", description = "메인 배너 관리 API (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/banners")
@RequiredArgsConstructor
public class BannerAdminController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final BannerAdminService bannerAdminService;

    @Operation(summary = "배너 이미지 업로드 주소 발급",
            description = "jpg·png·webp, 10MB 이하. uploadUrl로 PUT한 뒤 fileUrl로 배너를 등록한다. 주소는 5분 동안 유효하다.")
    @PostMapping("/image-upload-url")
    public ApiResponse<BannerImageUploadUrlResponse> issueImageUploadUrl(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @Valid @RequestBody BannerImageUploadUrlRequest request) {
        return ApiResponse.success(bannerAdminService.issueImageUploadUrl(adminId, request));
    }

    @Operation(summary = "배너 목록", description = "노출 순서대로. 삭제된 배너는 빠진다.")
    @GetMapping
    public ApiResponse<List<AdminBannerResponse>> getBanners(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId) {
        return ApiResponse.success(bannerAdminService.getBanners(adminId));
    }

    @Operation(summary = "배너 등록", description = "업로드 주소로 올린 이미지만 등록할 수 있다. 맨 뒤에 붙고, 최대 10개다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminBannerResponse> create(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @Valid @RequestBody BannerImageRequest request) {
        return ApiResponse.success(bannerAdminService.create(adminId, request.imageUrl()));
    }

    @Operation(summary = "배너 이미지 교체", description = "업로드 주소로 올린 이미지만 쓸 수 있다. 순서는 그대로다.")
    @PatchMapping("/{bannerId}")
    public ApiResponse<AdminBannerResponse> changeImage(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @PathVariable Long bannerId,
            @Valid @RequestBody BannerImageRequest request) {
        return ApiResponse.success(bannerAdminService.changeImage(adminId, bannerId, request.imageUrl()));
    }

    @Operation(summary = "배너 순서 변경", description = "등록된 배너 ID 전체를 원하는 순서로 보낸다. 하나라도 빠지거나 중복되면 전체가 거부된다.")
    @PutMapping("/order")
    public ApiResponse<List<AdminBannerResponse>> reorder(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @Valid @RequestBody BannerOrderUpdateRequest request) {
        return ApiResponse.success(bannerAdminService.reorder(adminId, request.bannerIds()));
    }

    @Operation(summary = "배너 삭제", description = "앱에서 바로 사라진다(소프트 삭제).")
    @DeleteMapping("/{bannerId}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @PathVariable Long bannerId) {
        bannerAdminService.delete(adminId, bannerId);
        return ApiResponse.success();
    }
}
