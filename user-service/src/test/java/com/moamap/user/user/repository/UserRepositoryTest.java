package com.moamap.user.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.user.user.entity.User;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
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

    /*
     * 델타: 06_architect_blueprint_nickname.md 6장 12단계.
     * 이슈 #63 활동내역 로그의 actorId -> 닉네임 벌크 조회(UserProfileService)가 쓰는 쿼리 메서드.
     * User 엔티티에는 소프트 삭제용 setter가 없어(운영 삭제 기능 미구현), 리플렉션으로 deletedAt을
     * 직접 채워 "탈퇴자"를 흉내낸다.
     */

    private void 탈퇴자로_만든다(User user) {
        try {
            Field field = User.class.getDeclaredField("deletedAt");
            field.setAccessible(true);
            field.set(user, Instant.now());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void findAllByIdInAndDeletedAtIsNull은_존재하는_id만_반환한다() {
        User saved = userRepository.save(User.createSocialUser("kakao", "111", "민서", "a@b.com", null));

        List<User> found = userRepository.findAllByIdInAndDeletedAtIsNull(List.of(saved.getId()));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getNickname()).isEqualTo("민서");
    }

    @Test
    void findAllByIdInAndDeletedAtIsNull은_없는_id는_결과에서_누락시킨다() {
        User saved = userRepository.save(User.createSocialUser("kakao", "111", "민서", "a@b.com", null));

        List<User> found = userRepository.findAllByIdInAndDeletedAtIsNull(List.of(saved.getId(), 9999L));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(saved.getId());
    }

    @Test
    void findAllByIdInAndDeletedAtIsNull은_소프트_삭제된_사용자를_제외한다() {
        User active = userRepository.save(User.createSocialUser("kakao", "111", "활성", "a@b.com", null));
        User withdrawn = userRepository.save(User.createSocialUser("kakao", "222", "탈퇴자", "b@b.com", null));
        탈퇴자로_만든다(withdrawn);
        userRepository.saveAndFlush(withdrawn);

        List<User> found = userRepository.findAllByIdInAndDeletedAtIsNull(List.of(active.getId(), withdrawn.getId()));

        assertThat(found).extracting(User::getId).containsExactly(active.getId());
    }
}
