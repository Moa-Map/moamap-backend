package com.moamap.user.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.user.dto.UserProfileResponse;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 06_architect_blueprint_nickname.md 2-1장 표 G(요청 id별 응답 동작)와
 * 6장 14단계(빈 목록/100개 초과 검증, distinct 처리)를 검증한다.
 * 실제 쿼리(존재하지 않는 id/탈퇴자 필터링)는 UserRepositoryTest(@DataJpaTest)가 담당하므로
 * 여기서는 UserRepository를 스텁으로 두고 서비스의 검증·매핑 로직만 본다.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private User user(Long id, String nickname, String profileImageUrl) {
        User user = User.createSocialUser("kakao", "provider-" + id, nickname, null, profileImageUrl);
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    @Test
    void 표G_정상_조회는_요청한_id_전부의_프로필을_반환한다() {
        // given
        given(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
            .willReturn(List.of(user(1L, "민서", "http://img/1"), user(2L, "길동", "http://img/2")));

        // when
        List<UserProfileResponse> result = userProfileService.findProfiles(List.of(1L, 2L));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserProfileResponse::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result).extracting(UserProfileResponse::nickname).containsExactlyInAnyOrder("민서", "길동");
    }

    @Test
    void 표G_존재하지_않는_id는_응답_배열에서_누락된다() {
        // given: 리포지토리가 이미 존재하는 id(1L)에 대한 결과만 돌려주는 상황
        given(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
            .willReturn(List.of(user(1L, "민서", null)));

        // when
        List<UserProfileResponse> result = userProfileService.findProfiles(List.of(1L, 9999L));

        // then: null 원소를 채우지 않고 그냥 1건만 반환한다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void 표G_소프트_삭제된_사용자는_리포지토리가_이미_걸러주므로_응답에서_누락된다() {
        // given: 탈퇴자 필터링 자체는 UserRepository 쿼리(findAllByIdInAndDeletedAtIsNull)의 책임이라
        // 여기서는 "리포지토리가 탈퇴자를 빼고 돌려줬을 때 서비스가 그대로 전달하는지"만 확인한다.
        given(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
            .willReturn(List.of(user(1L, "활성", null))); // 2L(탈퇴자)은 리포지토리 결과에 없음

        // when
        List<UserProfileResponse> result = userProfileService.findProfiles(List.of(1L, 2L));

        // then
        assertThat(result).extracting(UserProfileResponse::id).containsExactly(1L);
    }

    @Test
    void 표G_같은_id가_중복_전달되면_distinct된_목록으로_리포지토리를_1번만_호출한다() {
        // given
        given(userRepository.findAllByIdInAndDeletedAtIsNull(anyList()))
            .willReturn(List.of(user(1L, "민서", null)));
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);

        // when
        userProfileService.findProfiles(List.of(1L, 1L, 1L));

        // then: 중복 제거된 [1L] 하나로만 조회한다
        verify(userRepository).findAllByIdInAndDeletedAtIsNull(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(1L);
    }

    @Test
    void 표G_id가_0개면_BusinessException을_던지고_리포지토리를_호출하지_않는다() {
        // when & then
        assertThatThrownBy(() -> userProfileService.findProfiles(List.of()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
        verify(userRepository, org.mockito.Mockito.never()).findAllByIdInAndDeletedAtIsNull(anyList());
    }

    @Test
    void 표G_id가_101개_이상이면_TOO_MANY_USER_IDS를_던지고_리포지토리를_호출하지_않는다() {
        // given: 서로 다른 101개의 id (distinct 이후에도 101개로 유지되는, 애매하지 않은 케이스)
        List<Long> ids = IntStream.rangeClosed(1, 101).mapToObj(Long::valueOf).collect(Collectors.toList());

        // when & then
        assertThatThrownBy(() -> userProfileService.findProfiles(ids))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(UserErrorCode.TOO_MANY_USER_IDS);
        verify(userRepository, org.mockito.Mockito.never()).findAllByIdInAndDeletedAtIsNull(anyList());
    }

    @Test
    void 표G_id가_정확히_100개면_통과한다() {
        // given: 상한 100은 포함(inclusive) 경계다
        List<Long> ids = IntStream.rangeClosed(1, 100).mapToObj(Long::valueOf).collect(Collectors.toList());
        given(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).willReturn(List.of());

        // when & then: 예외 없이 통과해야 한다
        org.assertj.core.api.Assertions.assertThatCode(() -> userProfileService.findProfiles(ids))
            .doesNotThrowAnyException();
    }
}
