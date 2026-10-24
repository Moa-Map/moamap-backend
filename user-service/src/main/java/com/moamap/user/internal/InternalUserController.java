package com.moamap.user.internal;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.block.repository.UserBlockRepository;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스만 부르는 내부 API. 관리자 권한은 user-service만 알고 있어, 다른 서비스가 운영자 여부를 여기서 확인한다.
 *
 * 게이트웨이는 /internal 경로를 연결하지 않으므로 외부에서는 닿지 않는다. 공개 Swagger 문서에도 싣지 않는다.
 */
@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalUserController {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    /** 운영자(관리자)인가. 탈퇴한 회원은 운영자가 아니다. */
    @GetMapping("/{userId}/admin")
    public ApiResponse<AdminCheckResponse> isAdmin(@PathVariable Long userId) {
        boolean admin = userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn() && user.getRole() == Role.ADMIN)
                .isPresent();
        return ApiResponse.success(new AdminCheckResponse(admin));
    }

    /** 운영자 계정 ID 전체. 신고 알림을 받을 사람이다. */
    @GetMapping("/admins")
    public ApiResponse<List<Long>> adminIds() {
        return ApiResponse.success(userRepository.findIdsByRole(Role.ADMIN));
    }

    /** 이 사용자가 차단한 사람 ID. 다른 서비스가 목록에서 이 사람들의 콘텐츠를 뺀다. */
    @GetMapping("/{userId}/blocked-ids")
    public ApiResponse<List<Long>> blockedIds(@PathVariable Long userId) {
        return ApiResponse.success(userBlockRepository.findBlockedIdsByBlockerId(userId));
    }

    public record AdminCheckResponse(boolean admin) {
    }
}
