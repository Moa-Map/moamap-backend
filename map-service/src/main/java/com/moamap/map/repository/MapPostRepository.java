package com.moamap.map.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import com.moamap.map.entity.MapPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapPostRepository extends JpaRepository<MapPost, Long> {

    Page<MapPost> findByMapIdAndDeletedAtIsNull(Long mapId, Pageable pageable);

    Optional<MapPost> findByIdAndDeletedAtIsNull(Long id);

    /** 탈퇴 회원의 글을 한 번에 지운다. 작성자가 직접 지울 때와 같은 소프트 삭제다. 이미 지운 글은 건드리지 않는다. */
    @Modifying
    @Query("update MapPost p set p.deletedAt = :deletedAt where p.userId = :userId and p.deletedAt is null")
    int softDeleteAllByUserId(@Param("userId") Long userId, @Param("deletedAt") LocalDateTime deletedAt);
}
