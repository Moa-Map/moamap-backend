package com.moamap.user.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.user.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void provider와_providerId로_저장된_회원을_조회한다() {
        userRepository.save(User.createSocialUser("kakao", "111", "닉네임", "a@b.com", null));

        Optional<User> found = userRepository.findByProviderAndProviderId("kakao", "111");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("닉네임");
    }

    @Test
    void 같은_provider_providerId_조합은_중복_저장되지_않는다() {
        userRepository.save(User.createSocialUser("kakao", "111", "a", null, null));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(User.createSocialUser("kakao", "111", "b", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
