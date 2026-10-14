package com.moamap.user.user.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 관리자 전용 API의 권한 확인. 게이트웨이는 누구인지(X-User-Id)만 확인하고, 권한은 서비스가 회원 역할로 본다.
 *
 * 탈퇴한 관리자 계정은 남은 토큰으로 들어와도 막는다. 없는 회원·일반 회원·탈퇴 회원 모두 같은 403이라
 * 응답으로 관리자 계정 여부를 떠볼 수 없다.
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final UserRepository userRepository;

    public void requireAdmin(Long userId) {
        userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn() && user.getRole() == Role.ADMIN)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.ACCESS_DENIED));
    }
}
