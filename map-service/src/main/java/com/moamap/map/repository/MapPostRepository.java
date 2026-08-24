package com.moamap.map.repository;

import java.util.Optional;
import com.moamap.map.entity.MapPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapPostRepository extends JpaRepository<MapPost, Long> {

    Page<MapPost> findByMapIdAndDeletedAtIsNull(Long mapId, Pageable pageable);

    Optional<MapPost> findByIdAndDeletedAtIsNull(Long id);
}
