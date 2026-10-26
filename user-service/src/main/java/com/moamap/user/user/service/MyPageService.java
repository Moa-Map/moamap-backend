package com.moamap.user.user.service;

import com.moamap.user.user.dto.MyPageResponse;
import com.moamap.user.user.dto.UpdateMyPageRequest;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MyPageResponse getMyPage(Long userId) {
        User user = findUser(userId);
        return toResponse(user);
    }

    @Transactional
    public MyPageResponse updateMyPage(Long userId, UpdateMyPageRequest request) {
        User user = findUser(userId);
        user.updateProfile(request.nickname(), request.profileImageUrl(), request.email(), request.introduction());
        return toResponse(user);
    }

    private User findUser(Long userId) {
        // 탈퇴 직후에도 액세스 토큰은 만료 전까지 유효하다. 탈퇴 회원은 없는 회원으로 취급한다.
        return userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
    }

    private MyPageResponse toResponse(User user) {
        return new MyPageResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getProvider(),
                user.getRole(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getIntroduction()
        );
    }
}