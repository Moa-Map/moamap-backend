package com.moamap.user.auth.dev;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발용 카카오 로그인 콜백.
 *
 * 카카오 authorize 리다이렉트(redirect_uri)로 들어온 인가코드를 서버가 교환·로그인해
 * 우리 JWT를 브라우저에 바로 돌려준다. Swagger 테스트용 토큰을 원클릭으로 얻기 위한 것.
 * dev-login.enabled=true 일 때만 등록된다(운영에서는 엔드포인트 자체가 존재하지 않음).
 */
@RestController
@RequestMapping("/api/v1/auth/test/kakao")
@ConditionalOnProperty(prefix = "dev-login", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Tag(name = "DevAuth", description = "[개발 전용] 카카오 로그인 테스트 토큰 발급")
public class DevKakaoCallbackController {

    private final KakaoAuthCodeExchanger exchanger;
    private final AuthService authService;

    @GetMapping("/callback")
    @Operation(
        summary = "[개발 전용] 카카오 인가코드 콜백",
        description = "authorize 리다이렉트로 받은 code를 교환·로그인해 우리 JWT(TokenResponse)를 반환한다. "
            + "응답의 data.accessToken을 Swagger Authorize에 넣어 다른 API를 테스트한다."
    )
    public ApiResponse<TokenResponse> callback(@RequestParam String code) {
        String kakaoAccessToken = exchanger.exchange(code);
        return ApiResponse.success(authService.login(kakaoAccessToken));
    }
}
