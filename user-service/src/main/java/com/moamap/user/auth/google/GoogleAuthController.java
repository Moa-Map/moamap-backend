package com.moamap.user.auth.google;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.auth.dto.GoogleLoginRequest;
import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.service.GoogleLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/google")
@ConditionalOnProperty(prefix = "google", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class GoogleAuthController {
    private final GoogleLoginService googleLoginService;

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.success(googleLoginService.login(request.idToken()));
    }
}
