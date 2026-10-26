package com.moamap.user.inquiry.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.event.InquiryAnsweredEvent;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.inquiry.dto.InquiryDetailResponse;
import com.moamap.user.inquiry.dto.InquirySummaryResponse;
import com.moamap.user.inquiry.entity.Inquiry;
import com.moamap.user.inquiry.entity.InquiryStatus;
import com.moamap.user.inquiry.entity.InquiryType;
import com.moamap.user.inquiry.repository.InquiryRepository;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.user.service.AdminGuard;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 문의 열람·처리 상태 변경.
 *
 * 권한 확인을 문의 조회보다 먼저 한다. 관리자가 아니면 없는 문의 ID로 요청해도 403이라 존재 여부를 알 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryAdminService {

    static final int MAX_PAGE_SIZE = 50;
    private static final Sort LATEST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final InquiryRepository inquiryRepository;
    private final AdminGuard adminGuard;
    private final OutboxRecorder outboxRecorder;

    /** 최신 접수순. 상태·유형은 넘어온 것만 조건으로 건다. 클라이언트가 보낸 정렬은 무시한다. */
    public Page<InquirySummaryResponse> getInquiries(Long adminId, InquiryStatus status, InquiryType type,
                                                     Pageable pageable) {
        adminGuard.requireAdmin(adminId);
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), LATEST);
        return inquiryRepository.search(status, type, latestFirst).map(InquirySummaryResponse::from);
    }

    public InquiryDetailResponse getInquiry(Long adminId, Long inquiryId) {
        adminGuard.requireAdmin(adminId);
        return InquiryDetailResponse.from(getOrThrow(inquiryId));
    }

    @Transactional
    public InquiryDetailResponse changeStatus(Long adminId, Long inquiryId, InquiryStatus status) {
        adminGuard.requireAdmin(adminId);
        Inquiry inquiry = getOrThrow(inquiryId);
        boolean newlyAnswered = inquiry.getStatus() != InquiryStatus.ANSWERED && status == InquiryStatus.ANSWERED;
        Instant now = Instant.now();
        inquiry.changeStatus(status, now);
        if (newlyAnswered) {
            outboxRecorder.record(String.valueOf(inquiryId), InquiryAnsweredEvent.TYPE,
                    new InquiryAnsweredEvent(UUID.randomUUID().toString(), inquiry.getUserId(), inquiryId, now));
        }
        // 응답에 바뀐 수정 시각을 담으려면 지금 반영해야 한다(@PreUpdate는 flush 때 실행된다).
        inquiryRepository.flush();
        return InquiryDetailResponse.from(inquiry);
    }

    private Inquiry getOrThrow(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.INQUIRY_NOT_FOUND));
    }
}
