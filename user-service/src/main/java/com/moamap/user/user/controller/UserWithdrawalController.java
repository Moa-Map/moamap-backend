package com.moamap.user.user.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.user.service.UserWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "회원 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserWithdrawalController {

    private final UserWithdrawalService userWithdrawalService;

    @Operation(summary = "회원 탈퇴",
            description = "즉시 탈퇴하고 개인정보를 지운다. 되돌릴 수 없다. "
                    + "카카오 회원은 호출 전에 앱에서 카카오 SDK로 연결 끊기를 먼저 해야 한다. "
                    + "성공하면 앱은 저장된 토큰을 모두 지운다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        userWithdrawalService.withdraw(userId);
        return ApiResponse.success();
    }
}
