package com.moamap.user.auth.apple;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.auth.dto.AppleLoginRequest;
import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.service.AppleLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/apple")
@ConditionalOnProperty(prefix = "apple", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AppleAuthController {
    private final AppleLoginService appleLoginService;

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody AppleLoginRequest request) {
        return ApiResponse.success(appleLoginService.login(request));
    }
}
