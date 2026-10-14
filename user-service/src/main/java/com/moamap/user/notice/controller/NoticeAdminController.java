package com.moamap.user.notice.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.notice.dto.NoticeCreateRequest;
import com.moamap.user.notice.dto.NoticeResponse;
import com.moamap.user.notice.dto.NoticeUpdateRequest;
import com.moamap.user.notice.service.NoticeAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용. 권한은 서비스가 회원 역할(Role.ADMIN)로 확인한다.
 */
@Tag(name = "Notice Admin", description = "공지사항 관리 API (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/notices")
@RequiredArgsConstructor
public class NoticeAdminController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final NoticeAdminService noticeAdminService;

    @Operation(summary = "공지 등록", description = "제목 100자, 본문 5000자 이하. 본문은 일반 텍스트다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NoticeResponse> create(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @Valid @RequestBody NoticeCreateRequest request) {
        return ApiResponse.success(noticeAdminService.create(adminId, request));
    }

    @Operation(summary = "공지 수정", description = "보낸 값만 바꾼다. 제목과 본문 중 하나 이상 보내야 한다.")
    @PatchMapping("/{noticeId}")
    public ApiResponse<NoticeResponse> update(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeUpdateRequest request) {
        return ApiResponse.success(noticeAdminService.update(adminId, noticeId, request));
    }

    @Operation(summary = "공지 삭제", description = "앱에서 사라지고 기록은 남는다(소프트 삭제).")
    @DeleteMapping("/{noticeId}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long adminId,
            @PathVariable Long noticeId) {
        noticeAdminService.delete(adminId, noticeId);
        return ApiResponse.success();
    }
}
