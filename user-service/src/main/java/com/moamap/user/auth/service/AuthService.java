package com.moamap.user.auth.service;

import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.jwt.JwtProperties;
import com.moamap.user.auth.jwt.JwtProvider;
import com.moamap.user.auth.oauth.OAuthClient;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import com.moamap.user.refreshtoken.RefreshTokenStore;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final OAuthClient oAuthClient;
    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse login(String kakaoAccessToken) {
        OAuthUserInfo info = oAuthClient.getUserInfo(kakaoAccessToken);
        User user = userRepository.findByProviderAndProviderId(info.provider(), info.providerId())
                .orElseGet(() -> userRepository.save(User.createSocialUser(
                        info.provider(), info.providerId(),
                        info.nickname(), info.email(), info.profileImageUrl())));
        user.updateLastLogin(LocalDateTime.now());
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = UUID.randomUUID().toString();
        Duration ttl = Duration.ofDays(jwtProperties.refreshTokenExpirationDays());
        refreshTokenStore.save(refreshToken, user.getId(), ttl);
        return new TokenResponse(
                accessToken, refreshToken, "Bearer", jwtProvider.getAccessTokenExpiresInSeconds());
    }
}
