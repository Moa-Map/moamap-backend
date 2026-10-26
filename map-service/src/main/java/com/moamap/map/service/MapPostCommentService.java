package com.moamap.map.service;

import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.dto.MapPostCommentCreateRequest;
import com.moamap.map.dto.MapPostCommentResponse;
import com.moamap.map.dto.PageResponse;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import com.moamap.map.entity.MapRole;
import com.moamap.map.event.ActivitySignal;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import com.moamap.map.user.UserBlockClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그 탭 게시물의 댓글.
 *
 * 작성은 커뮤니티 지도에서도 멤버만 가능하다(읽기만 열려 있다). 삭제된 게시물은 조회 자체가 404라
 * 그 댓글도 자연히 보이지 않는다 — 댓글 행은 남긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapPostCommentService {

    private final MapPostCommentRepository commentRepository;
    private final MapPostRepository mapPostRepository;
    private final MapPostAccessPolicy accessPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final UserBlockClient userBlockClient;

    @Transactional
    public MapPostCommentResponse create(Long mapId, Long postId, MapPostCommentCreateRequest request, Long userId) {
        accessPolicy.requireWritable(mapId, userId);
        MapPost post = getPostOrThrow(mapId, postId);

        MapPostComment comment = commentRepository.save(MapPostComment.create(post.getId(), userId, request.content()));
        eventPublisher.publishEvent(
            new ActivitySignal(ActivitySignal.MAP_POST_COMMENTED, post.getUserId(), userId, postId, mapId));
        return MapPostCommentResponse.from(comment);
    }

    public PageResponse<MapPostCommentResponse> findAll(Long mapId, Long postId, Pageable pageable, Long userId) {
        accessPolicy.requireReadable(mapId, userId);
        getPostOrThrow(mapId, postId);

        // 요청한 사람이 차단한 사용자의 댓글은 빼고 준다.
        List<Long> blockedIds = userBlockClient.findBlockedIds(userId);
        Page<MapPostComment> comments = blockedIds.isEmpty()
            ? commentRepository.findByMapPostIdAndDeletedAtIsNull(postId, pageable)
            : commentRepository.findByMapPostIdAndDeletedAtIsNullAndUserIdNotIn(postId, blockedIds, pageable);
        return PageResponse.from(comments.map(MapPostCommentResponse::from));
    }

    @Transactional
    public void delete(Long mapId, Long postId, Long commentId, Long userId) {
        MapRole role = accessPolicy.requireWritable(mapId, userId);
        getPostOrThrow(mapId, postId);

        MapPostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.MAP_POST_COMMENT_NOT_FOUND));
        if (!comment.getMapPostId().equals(postId)) {
            throw new BusinessException(MapErrorCode.MAP_POST_COMMENT_NOT_FOUND);
        }
        if (!accessPolicy.canDelete(comment.isWrittenBy(userId), role)) {
            throw new BusinessException(MapErrorCode.NO_MANAGE_PERMISSION);
        }
        comment.delete();
    }

    private MapPost getPostOrThrow(Long mapId, Long postId) {
        MapPost post = mapPostRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.MAP_POST_NOT_FOUND));
        if (!post.getMapId().equals(mapId)) {
            throw new BusinessException(MapErrorCode.MAP_POST_NOT_FOUND);
        }
        return post;
    }
}
