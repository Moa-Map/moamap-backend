package com.moamap.map.repository;

import java.util.Collection;
import java.util.List;
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

    /**
     * 추천 후보의 id를 관심 태그와 많이 겹치는 순으로 뽑는다.
     *
     * 태그를 함께 조회하지 않고 id만 먼저 추리는 이유는, 컬렉션 fetch join과 페이징을 같이 쓰면
     * Hibernate가 전체를 메모리로 올린 뒤 잘라내기 때문이다. id로 범위를 좁힌 뒤 별도 조회로 태그를 채운다.
     */
    @Query("select m.id from MapEntity m join m.tags t "
        + "where m.type = :type and t in :tags "
        + "group by m.id, m.memberCount, m.createdAt "
        + "order by count(t) desc, m.memberCount desc, m.createdAt desc")
    List<Long> findCandidateIdsByTags(@Param("type") MapType type,
                                      @Param("tags") Collection<String> tags,
                                      Pageable pageable);

    /**
     * 관심 태그가 없거나 후보가 모자랄 때 채워 넣을 인기 지도 id.
     */
    @Query("select m.id from MapEntity m where m.type = :type "
        + "order by m.memberCount desc, m.createdAt desc")
    List<Long> findPopularIds(@Param("type") MapType type, Pageable pageable);

    /**
     * 점수 계산에 태그가 필요하므로 fetch join으로 함께 읽어 N+1을 막는다.
     */
    @Query("select distinct m from MapEntity m left join fetch m.tags where m.id in :ids")
    List<MapEntity> findAllWithTagsByIdIn(@Param("ids") Collection<Long> ids);
}
