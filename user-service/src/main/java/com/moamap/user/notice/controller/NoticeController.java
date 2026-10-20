package com.moamap.user.notice.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.notice.dto.NoticeResponse;
import com.moamap.user.notice.service.NoticeService;
import com.moamap.user.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notice", description = "공지사항 조회 API")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "공지 목록", description = "최신 등록순. 기본 20개, 최대 50개씩. 로그인 없이 조회할 수 있다. 공지가 없으면 빈 목록이다.")
    @GetMapping
    public ApiResponse<PageResponse<NoticeResponse>> getNotices(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(noticeService.getNotices(pageable)));
    }

    @Operation(summary = "공지 상세", description = "삭제됐거나 없는 공지는 404. 로그인 없이 조회할 수 있다.")
    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeResponse> getNotice(@PathVariable Long noticeId) {
        return ApiResponse.success(noticeService.getNotice(noticeId));
    }
}
