package com.moamap.user.user.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.user.dto.MyPageResponse;
import com.moamap.user.user.dto.UpdateMyPageRequest;
import com.moamap.user.user.service.MyPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    // X-User-Id는 게이트웨이가 JWT를 검증한 뒤 주입하는 헤더 (place-service와 동일한 규약).
    @GetMapping("/me")
    public ApiResponse<MyPageResponse> getMyPage(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(myPageService.getMyPage(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<MyPageResponse> updateMyPage(@RequestHeader("X-User-Id") Long userId,
                                                     @Valid @RequestBody UpdateMyPageRequest request) {
        return ApiResponse.success(myPageService.updateMyPage(userId, request));
    }
}