package com.moamap.map.repository;

import java.util.Optional;
import com.moamap.map.entity.MapPostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapPostCommentRepository extends JpaRepository<MapPostComment, Long> {

    Page<MapPostComment> findByMapPostIdAndDeletedAtIsNull(Long mapPostId, Pageable pageable);

    Optional<MapPostComment> findByIdAndDeletedAtIsNull(Long id);
}
