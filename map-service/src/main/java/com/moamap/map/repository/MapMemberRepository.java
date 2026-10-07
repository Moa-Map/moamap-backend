package com.moamap.map.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapMemberRepository extends JpaRepository<MapMember, Long> {

    Optional<MapMember> findByMapIdAndUserId(Long mapId, Long userId);

    boolean existsByMapIdAndUserId(Long mapId, Long userId);

    List<MapMember> findByUserIdAndMapIdIn(Long userId, Collection<Long> mapIds);

    List<MapMember> findByUserId(Long userId);

    /** 순서 변경 대상. 요청한 지도 목록이 실제 내 참여 목록과 일치하는지 검증하는 데 쓴다. */
    @Query("select mm from MapMember mm where mm.userId = :userId "
        + "and mm.mapId in (select m.id from MapEntity m where m.type = :type)")
    List<MapMember> findByUserIdAndMapType(@Param("userId") Long userId, @Param("type") MapType type);

    List<MapMember> findByMapId(Long mapId);

    @Modifying
    @Query("delete from MapMember mm where mm.mapId = :mapId")
    void deleteByMapId(@Param("mapId") Long mapId);
}
