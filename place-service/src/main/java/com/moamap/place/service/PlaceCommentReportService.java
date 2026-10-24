package com.moamap.place.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PlaceCommentReportRequest;
import com.moamap.place.dto.PlaceCommentReportResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceComment;
import com.moamap.place.entity.PlaceCommentReport;
import com.moamap.place.event.ReportFiled;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.repository.PlaceCommentReportRepository;
import com.moamap.place.repository.PlaceCommentRepository;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 신고. 운영자가 받는다(방장·관리자에게는 가지 않는다 — 방장·관리자는 장소 신고만 받는다).
 *
 * 그 댓글을 볼 수 있는 사람이면 신고할 수 있다(MapViewPolicy). 커뮤니티 지도를 둘러보는 사람도 신고할 수 있어야 한다.
 * 신고가 쌓여도 서버가 자동으로 숨기거나 지우지 않는다. 임계치를 정할 근거 데이터가 아직 없고,
 * 복구 수단 없이 자동 삭제를 넣으면 소수의 악의적 신고로 정상 댓글이 되돌릴 수 없게 사라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceCommentReportService {

    private static final String DUPLICATE_REPORT_CONSTRAINT = "uk_place_comment_reports_comment_reporter";

    private final PlaceCommentRepository placeCommentRepository;
    private final PlaceCommentReportRepository placeCommentReportRepository;
    private final PlaceRepository placeRepository;
    private final MapClient mapClient;
    private final MapViewPolicy mapViewPolicy;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PlaceCommentReportResponse report(Long placeId, Long commentId, Long userId,
            PlaceCommentReportRequest request) {
        requireLogin(userId);
        PlaceComment comment = getCommentOrThrow(placeId, commentId);
        Place place = getPlaceOrThrow(placeId);
        mapViewPolicy.requireViewable(mapClient.getMemberInfo(place.getMapId(), userId));

        if (comment.isWrittenBy(userId)) {
            throw new BusinessException(PlaceErrorCode.CANNOT_REPORT_OWN_COMMENT);
        }
        // 흔한 중복은 사전 조회로 싸게 걸러내고, 동시 요청은 유니크 제약이 최종 방어선이 된다.
        if (placeCommentReportRepository.existsByPlaceCommentIdAndReporterId(commentId, userId)) {
            throw new BusinessException(PlaceErrorCode.ALREADY_REPORTED_COMMENT);
        }

        PlaceCommentReport report = PlaceCommentReport.of(commentId, userId, request.reason(), request.detail());
        try {
            placeCommentReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateReportConstraintViolation(e)) {
                throw new BusinessException(PlaceErrorCode.ALREADY_REPORTED_COMMENT);
            }
            throw e;
        }
        placeCommentRepository.increaseReportCount(commentId);
        eventPublisher.publishEvent(ReportFiled.toOperators(report.getId(), place.getMapId(), userId));
        return PlaceCommentReportResponse.from(report);
    }

    // 길이 초과 등 다른 무결성 위반까지 "이미 신고함"으로 뭉개지 않도록, 해당 제약 위반일 때만 변환한다.
    private boolean isDuplicateReportConstraintViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null && constraintName.toLowerCase().contains(DUPLICATE_REPORT_CONSTRAINT);
        }
        return false;
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private Place getPlaceOrThrow(Long placeId) {
        return placeRepository.findByIdAndDeletedAtIsNull(placeId)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private PlaceComment getCommentOrThrow(Long placeId, Long commentId) {
        PlaceComment comment = placeCommentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.COMMENT_NOT_FOUND));
        // 다른 장소의 댓글 id로 들어오면 없는 것으로 다룬다.
        if (!comment.getPlaceId().equals(placeId)) {
            throw new BusinessException(PlaceErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }
}
