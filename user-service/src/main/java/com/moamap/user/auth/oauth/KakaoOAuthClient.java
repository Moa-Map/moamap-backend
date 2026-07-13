package com.moamap.user.auth.oauth;

import com.moamap.user.auth.exception.InvalidOAuthTokenException;
import com.moamap.user.auth.oauth.dto.KakaoTokenInfo;
import com.moamap.user.auth.oauth.dto.KakaoUserResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoOAuthClient implements OAuthClient {

    private static final String PROVIDER = "kakao";

    private final RestClient restClient;
    private final KakaoProperties properties;

    public KakaoOAuthClient(RestClient.Builder builder, KakaoProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public OAuthUserInfo getUserInfo(String providerAccessToken) {
        verifyAppId(providerAccessToken);
        KakaoUserResponse response = fetchUser(providerAccessToken);
        return toUserInfo(response);
    }

    private void verifyAppId(String accessToken) {
        KakaoTokenInfo tokenInfo;
        try {
            tokenInfo = restClient.get()
                    .uri(properties.tokenInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoTokenInfo.class);
        } catch (RestClientException e) {
            throw new InvalidOAuthTokenException("카카오 토큰 정보 조회에 실패했습니다.");
        }
        if (tokenInfo == null || tokenInfo.appId() != properties.appId()) {
            throw new InvalidOAuthTokenException("카카오 토큰이 우리 앱에서 발급된 것이 아닙니다.");
        }
    }

    private KakaoUserResponse fetchUser(String accessToken) {
        KakaoUserResponse response;
        try {
            response = restClient.get()
                    .uri(properties.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientException e) {
            throw new InvalidOAuthTokenException("카카오 사용자 정보 조회에 실패했습니다.");
        }
        if (response == null) {
            // 빈 2xx 응답 → toUserInfo에서 NPE(500) 나기 전에 인증 예외(401)로 변환
            throw new InvalidOAuthTokenException("카카오 사용자 정보가 비어 있습니다.");
        }
        return response;
    }

    private OAuthUserInfo toUserInfo(KakaoUserResponse response) {
        String email = null;
        String nickname = null;
        String profileImageUrl = null;
        if (response.kakaoAccount() != null) {
            email = response.kakaoAccount().email();
            KakaoUserResponse.KakaoAccount.Profile profile = response.kakaoAccount().profile();
            if (profile != null) {
                nickname = profile.nickname();
                profileImageUrl = profile.profileImageUrl();
            }
        }
        return new OAuthUserInfo(
                PROVIDER, String.valueOf(response.id()), nickname, email, profileImageUrl);
    }
}
