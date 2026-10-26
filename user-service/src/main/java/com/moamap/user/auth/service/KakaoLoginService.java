package com.moamap.user.auth.service;

import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.oauth.KakaoOAuthClient;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final AuthService authService;

    // 외부 인증을 마친 뒤 별도 빈을 호출해야 회원 저장 시점부터 DB 트랜잭션이 시작된다.
    public TokenResponse login(String kakaoAccessToken) {
        OAuthUserInfo info = kakaoOAuthClient.getUserInfo(kakaoAccessToken);
        return authService.login(withDefaultNickname(info));
    }

    private OAuthUserInfo withDefaultNickname(OAuthUserInfo info) {
        if (info.nickname() != null && !info.nickname().isBlank()) {
            return info;
        }
        // 실제 동의항목 설정과 무관하게 누락된 응답에 대비해 기존 카카오 기본값을 유지한다.
        return new OAuthUserInfo(info.provider(), info.providerId(),
                "kakao_" + info.providerId(), info.email(), info.profileImageUrl());
    }
}
