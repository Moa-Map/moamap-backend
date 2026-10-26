package com.moamap.map.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.map.dto.ReportRequest;
import com.moamap.map.dto.ReportResponse;
import com.moamap.map.entity.MapContentReport;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import com.moamap.map.entity.MapType;
import com.moamap.map.entity.ReportTargetType;
import com.moamap.map.event.ReportFiled;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapContentReportRepository;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도·로그·로그 댓글 신고. 운영자만 받는다(방장·관리자에게는 가지 않는다).
 *
 * 그 콘텐츠를 볼 수 있는 사람이면 신고할 수 있다 — 커뮤니티 지도는 로그인한 누구나, 프라이빗 지도는 멤버만.
 * 신고가 쌓여도 자동으로 숨기지 않는다. 운영자가 보고 판단한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MapReportService {

    private final MapContentReportRepository reportRepository;
    private final MapEntityRepository mapRepository;
    private final MapMemberRepository mapMemberRepository;
    private final MapPostRepository mapPostRepository;
    private final MapPostCommentRepository mapPostCommentRepository;
    private final MapPostAccessPolicy accessPolicy;
    private final ApplicationEventPublisher eventPublisher;

    /** 지도 이름·소개·커버 사진 신고. 공식 지도는 운영자가 만든 것이라 대상이 아니다. */
    public ReportResponse reportMap(Long mapId, Long userId, ReportRequest request) {
        requireLogin(userId);
        MapEntity map = mapRepository.findById(mapId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.MAP_NOT_FOUND));
        if (map.getType() == MapType.OFFICIAL) {
            throw new BusinessException(MapErrorCode.OFFICIAL_MAP_NOT_REPORTABLE);
        }
        if (map.getType() == MapType.PRIVATE && !mapMemberRepository.existsByMapIdAndUserId(mapId, userId)) {
            throw new BusinessException(MapErrorCode.NOT_MAP_MEMBER);
        }
        if (map.isOwnedBy(userId)) {
            throw new BusinessException(MapErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }
        return save(ReportTargetType.MAP, mapId, mapId, userId, request);
    }

    public ReportResponse reportPost(Long mapId, Long postId, Long userId, ReportRequest request) {
        requireLogin(userId);
        accessPolicy.requireReadable(mapId, userId);
        MapPost post = getPostOrThrow(mapId, postId);
        if (post.isWrittenBy(userId)) {
            throw new BusinessException(MapErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }
        return save(ReportTargetType.MAP_POST, postId, mapId, userId, request);
    }

    public ReportResponse reportComment(Long mapId, Long postId, Long commentId, Long userId, ReportRequest request) {
        requireLogin(userId);
        accessPolicy.requireReadable(mapId, userId);
        getPostOrThrow(mapId, postId);
        MapPostComment comment = mapPostCommentRepository.findByIdAndDeletedAtIsNull(commentId)
            .filter(found -> found.getMapPostId().equals(postId))
            .orElseThrow(() -> new BusinessException(MapErrorCode.MAP_POST_COMMENT_NOT_FOUND));
        if (comment.isWrittenBy(userId)) {
            throw new BusinessException(MapErrorCode.CANNOT_REPORT_OWN_CONTENT);
        }
        return save(ReportTargetType.MAP_POST_COMMENT, commentId, mapId, userId, request);
    }

    private ReportResponse save(ReportTargetType targetType, Long targetId, Long mapId, Long userId,
                                ReportRequest request) {
        // 흔한 중복은 미리 걸러내고, 동시에 들어온 요청은 유일 제약이 막는다.
        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterId(targetType, targetId, userId)) {
            throw new BusinessException(MapErrorCode.ALREADY_REPORTED);
        }
        MapContentReport report;
        try {
            report = reportRepository.saveAndFlush(
                MapContentReport.of(targetType, targetId, mapId, userId, request.reason(), request.detail()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(MapErrorCode.ALREADY_REPORTED);
        }
        eventPublisher.publishEvent(new ReportFiled(report.getId(), mapId));
        return ReportResponse.from(report);
    }

    /** 다른 지도의 게시물 ID로 들어오면 없는 것으로 다룬다. */
    private MapPost getPostOrThrow(Long mapId, Long postId) {
        return mapPostRepository.findByIdAndDeletedAtIsNull(postId)
            .filter(post -> post.getMapId().equals(mapId))
            .orElseThrow(() -> new BusinessException(MapErrorCode.MAP_POST_NOT_FOUND));
    }

    private static void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }
}
