package com.moamap.user.report.service;

import com.moamap.user.report.dto.UserReportAdminResponse;
import com.moamap.user.report.entity.UserReport;
import com.moamap.user.report.repository.UserReportRepository;
import com.moamap.user.report.repository.UserReportRepository.TargetReportCount;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import com.moamap.user.user.service.AdminGuard;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 사용자 신고 확인. 신고자 정보는 운영자에게만 보인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReportAdminService {

    static final int MAX_PAGE_SIZE = 50;

    private static final Sort LATEST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final AdminGuard adminGuard;

    public Page<UserReportAdminResponse> getReports(Long adminId, Pageable pageable) {
        adminGuard.requireAdmin(adminId);
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), LATEST);
        Page<UserReport> reports = userReportRepository.findAllBy(latestFirst);

        List<Long> targetIds = reports.stream().map(UserReport::getTargetUserId).distinct().toList();
        Map<Long, User> targets = userRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Long> reportCounts = userReportRepository.countByTargetUserIds(targetIds).stream()
                .collect(Collectors.toMap(TargetReportCount::getTargetUserId, TargetReportCount::getReportCount));
        return reports.map(report -> UserReportAdminResponse.of(report, targets.get(report.getTargetUserId()),
                reportCounts.getOrDefault(report.getTargetUserId(), 0L)));
    }
}
