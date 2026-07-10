package com.moamap.user.refreshtoken.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moamap.user.refreshtoken.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void 토큰_문자열로_조회한다() {
        refreshTokenRepository.save(
                RefreshToken.issue(1L, "refresh-abc", LocalDateTime.now().plusDays(14)));

        Optional<RefreshToken> found = refreshTokenRepository.findByToken("refresh-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void 토큰_문자열로_삭제한다() {
        refreshTokenRepository.save(
                RefreshToken.issue(1L, "refresh-abc", LocalDateTime.now().plusDays(14)));

        refreshTokenRepository.deleteByToken("refresh-abc");

        assertThat(refreshTokenRepository.findByToken("refresh-abc")).isEmpty();
    }
}
