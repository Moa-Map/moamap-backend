package com.moamap.map.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.moamap.map.entity.MapMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapMemberRepository extends JpaRepository<MapMember, Long> {

    Optional<MapMember> findByMapIdAndUserId(Long mapId, Long userId);

    boolean existsByMapIdAndUserId(Long mapId, Long userId);

    List<MapMember> findByUserIdAndMapIdIn(Long userId, Collection<Long> mapIds);

    @Modifying
    @Query("delete from MapMember mm where mm.mapId = :mapId")
    void deleteByMapId(@Param("mapId") Long mapId);
}
