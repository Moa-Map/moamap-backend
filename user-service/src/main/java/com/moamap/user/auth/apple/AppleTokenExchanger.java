package com.moamap.user.auth.apple;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class AppleTokenExchanger {
    static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private final RestClient client;
    private final AppleProperties properties;
    private final AppleClientSecretProvider secrets;
    private final AppleIdentityTokenVerifier verifier;
    private final ObjectMapper mapper;

    public AppleTokenExchanger(RestClient client, AppleProperties properties, AppleClientSecretProvider secrets,
                               AppleIdentityTokenVerifier verifier, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.secrets = secrets;
        this.verifier = verifier;
        this.mapper = mapper;
    }

    /** expected에는 최초 Identity Token 검증과 nonce 소비가 끝난 결과를 전달한다. */
    public Result exchange(String code, AppleIdentity expected) {
        if (code == null || code.isBlank() || code.length() > 4096 || expected == null
                || expected.subject() == null || expected.subject().isBlank()
                || !AppleNonceService.isValidFormat(expected.nonce())) {
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", secrets.create());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        // iOS 네이티브 인증에는 redirect_uri를 보내지 않는다. 인가 코드는 한 번만 교환하며 자동 재시도하지 않는다.
        TokenResponse response;
        try {
            response = client.post().uri(TOKEN_URI).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(TokenResponse.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException(isInvalidGrant(e) ? UserErrorCode.INVALID_APPLE_TOKEN : UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        } catch (RestClientException e) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        if (response == null || response.idToken() == null || response.idToken().isBlank()) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        AppleIdentity identity = verifier.verify(response.idToken(), expected.nonce());
        if (!expected.subject().equals(identity.subject())) {
            throw new BusinessException(UserErrorCode.INVALID_APPLE_TOKEN);
        }
        String refresh = response.refreshToken();
        if (refresh != null && refresh.length() > 16384) {
            throw new BusinessException(UserErrorCode.APPLE_AUTH_UNAVAILABLE);
        }
        return new Result(identity, refresh == null || refresh.isBlank() ? null : refresh);
    }

    private boolean isInvalidGrant(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 400 || exception.getResponseBodyAsByteArray().length > 16384) {
            return false;
        }
        try {
            var body = mapper.readTree(exception.getResponseBodyAsByteArray());
            return body != null && "invalid_grant".equals(body.path("error").asText());
        } catch (IOException e) {
            return false;
        }
    }

    private record TokenResponse(@JsonProperty("id_token") String idToken,
                                 @JsonProperty("refresh_token") String refreshToken) {
        @Override public String toString() { return "AppleTokenResponse[redacted]"; }
    }

    public record Result(AppleIdentity identity, String refreshToken) {
        @Override public String toString() { return "AppleTokenExchangeResult[redacted]"; }
    }
}
