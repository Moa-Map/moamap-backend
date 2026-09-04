package com.moamap.map.repository;

import java.util.Collection;
import java.util.List;
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

    // 탐색 탭용. 참여 중인 지도를 페이지네이션 전에 빼야 페이지 크기·전체 개수가 화면과 맞는다.
    @Query("select m from MapEntity m where m.type = :type "
        + "and m.id not in (select mm.mapId from MapMember mm where mm.userId = :userId)")
    Page<MapEntity> findNotJoinedByType(@Param("userId") Long userId, @Param("type") MapType type, Pageable pageable);

    @Query("select distinct m from MapEntity m join m.tags t where m.type = :type and t = :tag "
        + "and m.id not in (select mm.mapId from MapMember mm where mm.userId = :userId)")
    Page<MapEntity> findNotJoinedByTypeAndTag(@Param("userId") Long userId, @Param("type") MapType type,
                                              @Param("tag") String tag, Pageable pageable);

    /**
     * 공개 지도(커뮤니티·공식)를 이름/설명/태그로 검색한다. PRIVATE은 초대 코드로만 들어가는 지도라 제외한다.
     *
     * 태그 컬렉션을 조인하므로 distinct가 필요하고, 파생 count 쿼리는 이 중복을 그대로 세어
     * totalElements를 부풀린다. countQuery를 직접 준다.
     */
    @Query(
        value = "select distinct m from MapEntity m left join m.tags t "
            + "where m.type in :types "
            + "and (lower(m.name) like :keyword escape '\\' "
            + "or lower(m.description) like :keyword escape '\\' "
            + "or t like :keyword escape '\\')",
        countQuery = "select count(distinct m) from MapEntity m left join m.tags t "
            + "where m.type in :types "
            + "and (lower(m.name) like :keyword escape '\\' "
            + "or lower(m.description) like :keyword escape '\\' "
            + "or t like :keyword escape '\\')"
    )
    Page<MapEntity> search(@Param("types") Collection<MapType> types,
                           @Param("keyword") String keyword,
                           Pageable pageable);

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
    boolean existsByOwnerIdAndPersonalIsTrue(Long ownerId);

    // 엔티티를 로드해 setter를 쓰지 않고 벌크 UPDATE로 카운트 컬럼만 갱신한다.
    // 리스너가 지도 수정/멤버 가입과 동시에 실행돼도 다른 필드를 되돌릴 위험이 없다(청사진 3-3(나)).
    @Modifying(clearAutomatically = true)
    @Query("update MapEntity m set m.placeCount = :placeCount where m.id = :mapId")
    int updatePlaceCount(@Param("mapId") Long mapId, @Param("placeCount") long placeCount);
}
