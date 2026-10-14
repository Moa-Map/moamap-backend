package com.moamap.map.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import com.moamap.map.entity.MapPostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapPostCommentRepository extends JpaRepository<MapPostComment, Long> {

    Page<MapPostComment> findByMapPostIdAndDeletedAtIsNull(Long mapPostId, Pageable pageable);

    Optional<MapPostComment> findByIdAndDeletedAtIsNull(Long id);

    /** 탈퇴 회원의 글을 한 번에 지운다. 작성자가 직접 지울 때와 같은 소프트 삭제다. 이미 지운 글은 건드리지 않는다. */
    @Modifying
    @Query("update MapPostComment c set c.deletedAt = :deletedAt where c.userId = :userId and c.deletedAt is null")
    int softDeleteAllByUserId(@Param("userId") Long userId, @Param("deletedAt") LocalDateTime deletedAt);

    /** 지도를 지울 때 그 지도 게시글에 달린 댓글을 지운다. 댓글은 컬렉션이 없어 한 번에 지운다. */
    @Modifying
    @Query("delete from MapPostComment c where c.mapPostId in (select p.id from MapPost p where p.mapId = :mapId)")
    int deleteAllByMapId(@Param("mapId") Long mapId);
}
