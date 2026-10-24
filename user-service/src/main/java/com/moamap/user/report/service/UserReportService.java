package com.moamap.user.report.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.event.ReportReceivedEvent;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.outbox.OutboxRecorder;
import com.moamap.user.report.dto.ReportRequest;
import com.moamap.user.report.dto.ReportResponse;
import com.moamap.user.report.entity.UserReport;
import com.moamap.user.report.repository.UserReportRepository;
import com.moamap.user.user.entity.Role;
import com.moamap.user.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자(프로필) 신고.
 *
 * 신고가 쌓여도 자동으로 제재하지 않는다. 운영자가 보고 판단한다.
 * 운영자 알림은 신고와 같은 트랜잭션에 outbox로 남겨, 신고는 됐는데 알림만 빠지는 일이 없게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserReportService {

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final OutboxRecorder outboxRecorder;

    public ReportResponse report(Long reporterId, Long targetUserId, ReportRequest request) {
        if (reporterId.equals(targetUserId)) {
            throw new BusinessException(UserErrorCode.CANNOT_REPORT_SELF);
        }
        userRepository.findById(targetUserId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        // 흔한 중복은 미리 걸러내고, 동시에 들어온 요청은 유일 제약이 막는다.
        if (userReportRepository.existsByTargetUserIdAndReporterId(targetUserId, reporterId)) {
            throw new BusinessException(UserErrorCode.ALREADY_REPORTED_USER);
        }

        UserReport report;
        try {
            report = userReportRepository.saveAndFlush(
                    UserReport.of(targetUserId, reporterId, request.reason(), request.detail()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(UserErrorCode.ALREADY_REPORTED_USER);
        }
        notifyOperators(report);
        return ReportResponse.from(report);
    }

    private void notifyOperators(UserReport report) {
        for (Long adminId : userRepository.findIdsByRole(Role.ADMIN)) {
            outboxRecorder.record(String.valueOf(report.getId()), ReportReceivedEvent.TYPE,
                    new ReportReceivedEvent(UUID.randomUUID().toString(), adminId, report.getId(), report.getCreatedAt()));
        }
    }
}
