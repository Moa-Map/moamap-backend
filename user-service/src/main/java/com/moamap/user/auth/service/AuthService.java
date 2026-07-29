package com.moamap.user.auth.service;

import com.moamap.user.auth.dto.TokenResponse;
import com.moamap.user.auth.exception.RefreshTokenNotFoundException;
import com.moamap.user.auth.jwt.JwtProperties;
import com.moamap.user.event.UserRegisteredEvent;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.auth.jwt.JwtProvider;
import com.moamap.user.auth.oauth.OAuthClient;
import com.moamap.user.auth.oauth.OAuthUserInfo;
import com.moamap.user.refreshtoken.RefreshTokenStore;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
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
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public TokenResponse login(String kakaoAccessToken) {
        OAuthUserInfo info = oAuthClient.getUserInfo(kakaoAccessToken);
        Optional<User> existing = userRepository.findByProviderAndProviderId(info.provider(), info.providerId());
        boolean newUser = existing.isEmpty();
        User user = existing.orElseGet(() -> userRepository.save(User.createSocialUser(
                info.provider(), info.providerId(),
                resolveNickname(info), info.email(), info.profileImageUrl())));
        if (newUser) {
            recordRegistered(user.getId());
        }
        user.updateLastLogin(Instant.now());
        return issueTokens(user, newUser);
    }

    /**
     * 회원 저장과 같은 트랜잭션에 이벤트를 남긴다. 커밋되면 이벤트도 반드시 남고, 롤백되면 함께 사라진다.
     * 실제 발행은 OutboxPublisher가 커밋 이후에 맡는다.
     */
    private void recordRegistered(Long userId) {
        outboxRecorder.record(
            String.valueOf(userId),
            UserRegisteredEvent.TYPE,
            new UserRegisteredEvent(UUID.randomUUID().toString(), userId, Instant.now()));
    }

    // 닉네임은 카카오 동의항목에서 선택 동의라 거부 시 null로 온다. NOT NULL 컬럼이므로 기본값으로 대체.
    private String resolveNickname(OAuthUserInfo info) {
        if (info.nickname() != null && !info.nickname().isBlank()) {
            return info.nickname();
        }
        return "kakao_" + info.providerId();
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        // Redis TTL이 만료를 처리하므로, 만료됐거나 없으면 findUserId가 빈 값을 준다.
        Long userId = refreshTokenStore.findUserId(refreshToken)
                .orElseThrow(() -> new RefreshTokenNotFoundException("리프레시 토큰을 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RefreshTokenNotFoundException("사용자를 찾을 수 없습니다."));
        refreshTokenStore.delete(refreshToken); // 회전: 기존 토큰 폐기 후 새로 발급
        return issueTokens(user, false);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenStore.delete(refreshToken);
    }

    private TokenResponse issueTokens(User user, boolean newUser) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = UUID.randomUUID().toString();
        Duration ttl = Duration.ofDays(jwtProperties.refreshTokenExpirationDays());
        refreshTokenStore.save(refreshToken, user.getId(), ttl);
        return new TokenResponse(
                accessToken, refreshToken, "Bearer",
                jwtProvider.getAccessTokenExpiresInSeconds(), ttl.getSeconds(), newUser);
    }
}
