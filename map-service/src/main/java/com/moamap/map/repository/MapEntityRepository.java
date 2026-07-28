package com.moamap.map.repository;

import java.util.Optional;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 엔티티를 로드해 setter를 쓰지 않고 벌크 UPDATE로 카운트 컬럼만 갱신한다.
    // 리스너가 지도 수정/멤버 가입과 동시에 실행돼도 다른 필드를 되돌릴 위험이 없다(청사진 3-3(나)).
    @Modifying(clearAutomatically = true)
    @Query("update MapEntity m set m.placeCount = :placeCount where m.id = :mapId")
    int updatePlaceCount(@Param("mapId") Long mapId, @Param("placeCount") long placeCount);
}
