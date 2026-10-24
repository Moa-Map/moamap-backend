package com.moamap.place.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PlaceReportAdminResponse;
import com.moamap.place.entity.PlaceReportTargetType;
import com.moamap.place.user.UserAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 운영자의 장소·장소 댓글 신고 확인.
 *
 * 운영자 확인은 user-service를 부르므로 DB 트랜잭션(PlaceReportAdminReader)보다 먼저, 트랜잭션 밖에서 한다.
 * 그동안 DB 커넥션을 잡고 있지 않기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class PlaceReportAdminService {

    static final int MAX_PAGE_SIZE = 50;

    private static final Sort LATEST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final UserAdminClient userAdminClient;
    private final PlaceReportAdminReader reader;

    public Page<PlaceReportAdminResponse> getReports(Long adminId, PlaceReportTargetType targetType, Pageable pageable) {
        if (adminId == null || !userAdminClient.isAdmin(adminId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), LATEST);
        return targetType == PlaceReportTargetType.PLACE_COMMENT
            ? reader.readCommentReports(latestFirst)
            : reader.readPlaceReports(latestFirst);
    }
}
