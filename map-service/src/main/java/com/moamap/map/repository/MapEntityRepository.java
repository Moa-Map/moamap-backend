package com.moamap.map.repository;

import java.util.Optional;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapEntityRepository extends JpaRepository<MapEntity, Long> {

    Page<MapEntity> findByType(MapType type, Pageable pageable);

    @Query("select distinct m from MapEntity m join m.tags t where m.type = :type and t = :tag")
    Page<MapEntity> findByTypeAndTag(@Param("type") MapType type, @Param("tag") String tag, Pageable pageable);

    @Query("select m from MapEntity m where m.type = :type "
        + "and m.id in (select mm.mapId from MapMember mm where mm.userId = :userId)")
    Page<MapEntity> findJoinedByType(@Param("userId") Long userId, @Param("type") MapType type, Pageable pageable);

    Optional<MapEntity> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
