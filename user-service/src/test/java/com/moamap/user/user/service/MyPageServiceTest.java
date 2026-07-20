package com.moamap.user.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.moamap.user.user.dto.MyPageResponse;
import com.moamap.user.user.dto.UpdateMyPageRequest;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    @Mock private UserRepository userRepository;

    private MyPageService myPageService;

    @BeforeEach
    void setUp() {
        myPageService = new MyPageService(userRepository);
    }

    private static User 사용자(Long id, String nickname, String profileImageUrl) {
        User user = User.createSocialUser("kakao", "111", nickname, "a@b.com", profileImageUrl);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", Role.USER);
        return user;
    }

    @Test
    void 존재하는_사용자의_마이페이지_정보를_조회한다() {
        User user = 사용자(1L, "길동", "http://img");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.getMyPage(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("길동");
        assertThat(response.email()).isEqualTo("a@b.com");
        assertThat(response.profileImageUrl()).isEqualTo("http://img");
        assertThat(response.provider()).isEqualTo("kakao");
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.introduction()).isNull();
    }

    @Test
    void 자기소개가_설정된_사용자를_조회하면_자기소개를_반환한다() {
        User user = 사용자(1L, "길동", "http://img");
        ReflectionTestUtils.setField(user, "introduction", "안녕하세요");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.getMyPage(1L);

        assertThat(response.introduction()).isEqualTo("안녕하세요");
    }

    @Test
    void 존재하지_않는_사용자를_조회하면_예외를_던진다() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> myPageService.getMyPage(999L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void 닉네임만_전달하면_닉네임만_변경된다() {
        User user = 사용자(1L, "길동", "http://old");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.updateMyPage(1L, new UpdateMyPageRequest("새닉네임", null, null, null));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("http://old");
    }

    @Test
    void 프로필_이미지만_전달하면_프로필_이미지만_변경된다() {
        User user = 사용자(1L, "길동", "http://old");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.updateMyPage(1L, new UpdateMyPageRequest(null, "http://new", null, null));

        assertThat(response.nickname()).isEqualTo("길동");
        assertThat(response.profileImageUrl()).isEqualTo("http://new");
    }

    @Test
    void 닉네임과_프로필_이미지를_모두_전달하면_둘_다_변경된다() {
        User user = 사용자(1L, "길동", "http://old");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.updateMyPage(1L, new UpdateMyPageRequest("새닉네임", "http://new", null, null));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("http://new");
    }

    @Test
    void 존재하지_않는_사용자를_수정하면_예외를_던진다() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> myPageService.updateMyPage(999L, new UpdateMyPageRequest("새닉네임", null, null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void 이메일만_전달하면_이메일만_변경된다() {
        User user = 사용자(1L, "길동", "http://old");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.updateMyPage(1L, new UpdateMyPageRequest(null, null, "new@b.com", null));

        assertThat(response.email()).isEqualTo("new@b.com");
        assertThat(response.nickname()).isEqualTo("길동");
        assertThat(response.profileImageUrl()).isEqualTo("http://old");
    }

    @Test
    void 이메일과_닉네임과_프로필_이미지를_모두_전달하면_전부_변경된다() {
        User user = 사용자(1L, "길동", "http://old");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.updateMyPage(
                1L, new UpdateMyPageRequest("새닉네임", "http://new", "new@b.com", null));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("http://new");
        assertThat(response.email()).isEqualTo("new@b.com");
    }

    @Test
    void 자기소개만_전달하면_자기소개만_변경된다() {
        User user = 사용자(1L, "길동", "http://old");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.updateMyPage(
                1L, new UpdateMyPageRequest(null, null, null, "안녕하세요"));

        assertThat(response.introduction()).isEqualTo("안녕하세요");
        assertThat(response.nickname()).isEqualTo("길동");
        assertThat(response.profileImageUrl()).isEqualTo("http://old");
    }

    @Test
    void 자기소개와_다른_필드를_함께_전달하면_전부_변경된다() {
        User user = 사용자(1L, "길동", "http://old");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.updateMyPage(
                1L, new UpdateMyPageRequest("새닉네임", "http://new", "new@b.com", "안녕하세요"));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("http://new");
        assertThat(response.email()).isEqualTo("new@b.com");
        assertThat(response.introduction()).isEqualTo("안녕하세요");
    }

    @Test
    void 자기소개를_전달하지_않으면_기존_자기소개가_유지된다() {
        User user = 사용자(1L, "길동", "http://old");
        ReflectionTestUtils.setField(user, "introduction", "기존소개");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyPageResponse response = myPageService.updateMyPage(
                1L, new UpdateMyPageRequest("새닉네임", null, null, null));

        assertThat(response.introduction()).isEqualTo("기존소개");
    }
}
