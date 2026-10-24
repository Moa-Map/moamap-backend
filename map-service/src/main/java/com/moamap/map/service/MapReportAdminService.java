package com.moamap.map.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.map.dto.MapReportAdminResponse;
import com.moamap.map.entity.ReportTargetType;
import com.moamap.map.user.UserAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 운영자의 지도·로그·로그 댓글 신고 확인.
 *
 * 운영자 확인은 user-service를 부르므로 DB 트랜잭션(MapReportAdminReader)보다 먼저, 트랜잭션 밖에서 한다.
 * 그동안 DB 커넥션을 잡고 있지 않기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class MapReportAdminService {

    static final int MAX_PAGE_SIZE = 50;

    private static final Sort LATEST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final UserAdminClient userAdminClient;
    private final MapReportAdminReader reader;

    public Page<MapReportAdminResponse> getReports(Long adminId, ReportTargetType targetType, Pageable pageable) {
        if (adminId == null || !userAdminClient.isAdmin(adminId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), LATEST);
        return reader.read(targetType, latestFirst);
    }
}
