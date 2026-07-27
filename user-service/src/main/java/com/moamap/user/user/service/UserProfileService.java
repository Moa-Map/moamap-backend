package com.moamap.user.user.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.user.dto.UserProfileResponse;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// MyPageService(본인 전용, 전체 필드)와 노출 정책이 정반대라 의도적으로 분리한다.
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final int MAX_IDS = 100;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserProfileResponse> findProfiles(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        if (distinctIds.size() > MAX_IDS) {
            throw new BusinessException(UserErrorCode.TOO_MANY_USER_IDS);
        }
        return userRepository.findAllByIdInAndDeletedAtIsNull(distinctIds).stream()
            .map(this::toResponse)
            .toList();
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
