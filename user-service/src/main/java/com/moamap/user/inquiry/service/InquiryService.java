package com.moamap.user.inquiry.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.inquiry.dto.InquiryCreateRequest;
import com.moamap.user.inquiry.dto.InquiryCreateResponse;
import com.moamap.user.inquiry.entity.Inquiry;
import com.moamap.user.inquiry.repository.InquiryRepository;
import com.moamap.user.user.exception.UserNotFoundException;
import com.moamap.user.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 문의 등록.
 *
 * 로그인한 누구나 등록할 수 있어서 개수를 제한한다. 최근 24시간 동안 10건까지다.
 * 동시에 여러 건을 보내면 한두 건 넘칠 수 있지만, 도배를 막는 목적에는 충분하다.
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

    static final int DAILY_LIMIT = 10;
    private static final Duration LIMIT_WINDOW = Duration.ofHours(24);

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    @Transactional
    public InquiryCreateResponse create(Long userId, InquiryCreateRequest request) {
        requireActiveUser(userId);
        long recent = inquiryRepository.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(LIMIT_WINDOW));
        if (recent >= DAILY_LIMIT) {
            throw new BusinessException(UserErrorCode.TOO_MANY_INQUIRIES);
        }
        Inquiry inquiry = inquiryRepository.save(
                Inquiry.receive(userId, request.type(), request.replyEmail().strip(), request.content()));
        return InquiryCreateResponse.from(inquiry);
    }

    /** 탈퇴 직후에도 액세스 토큰은 만료 전까지 유효하다. 탈퇴 회원의 문의는 받지 않는다. */
    private void requireActiveUser(Long userId) {
        userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
    }
}
