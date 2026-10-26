package com.moamap.notification.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.notification.dto.NotificationResponse;
import com.moamap.notification.dto.UnreadCountResponse;
import com.moamap.notification.service.NotificationService;
import com.moamap.notification.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 알림. 게이트웨이가 검증한 X-User-Id로 본인을 판단하고, 남의 알림은 없는 알림과 똑같이 404다.
 */
@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록", description = "최신순. 기본 20개, 최대 50개씩. 90일이 지난 알림은 자동으로 지워진다.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(notificationService.getNotifications(userId, pageable)));
    }

    @Operation(summary = "안 읽은 알림 수", description = "종 아이콘 뱃지용.")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId) {
        return ApiResponse.success(notificationService.getUnreadCount(userId));
    }

    @Operation(summary = "알림 읽음", description = "이미 읽은 알림이어도 성공한다.")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long notificationId) {
        notificationService.markRead(userId, notificationId);
        return ApiResponse.success();
    }

    @Operation(summary = "모두 읽음", description = "안 읽은 알림이 없어도 성공한다.")
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.success();
    }

    @Operation(summary = "알림 삭제")
    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long notificationId) {
        notificationService.delete(userId, notificationId);
        return ApiResponse.success();
    }
}
