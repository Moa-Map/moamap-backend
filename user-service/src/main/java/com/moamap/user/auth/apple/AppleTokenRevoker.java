package com.moamap.user.auth.apple;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 탈퇴한 회원의 Apple 토큰을 폐기한다. Sign in with Apple을 쓰는 앱은 계정 삭제 시 이 요청이 App Store 심사 요건이다.
 *
 * 실패하면 RestClientException을 그대로 던진다. 탈퇴는 이미 끝난 뒤라 되돌리지 않고, 호출부가 기록만 남긴다.
 */
public class AppleTokenRevoker {

    static final String REVOKE_URI = "https://appleid.apple.com/auth/revoke";

    private final RestClient client;
    private final AppleProperties properties;
    private final AppleClientSecretProvider secrets;

    public AppleTokenRevoker(RestClient client, AppleProperties properties, AppleClientSecretProvider secrets) {
        this.client = client;
        this.properties = properties;
        this.secrets = secrets;
    }

    public void revoke(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", secrets.create());
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        client.post().uri(REVOKE_URI).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().toBodilessEntity();
    }
}
