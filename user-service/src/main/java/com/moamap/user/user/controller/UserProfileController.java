package com.moamap.user.user.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.user.dto.UserProfileResponse;
import com.moamap.user.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// MyPageController(본인 전용, 전체 필드)와 노출 정책이 정반대라 분리한다.
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/profiles")
    @Operation(
        summary = "공개 프로필 벌크 조회 (서비스 간 호출용)",
        description = "여러 사용자의 공개 프로필(id, nickname, profileImageUrl)을 한 번에 조회한다. "
            + "id는 1~100개, 존재하지 않거나 탈퇴한 사용자는 응답 배열에서 누락된다."
    )
    public ApiResponse<List<UserProfileResponse>> getProfiles(@RequestParam List<Long> ids) {
        return ApiResponse.success(userProfileService.findProfiles(ids));
    }
}
