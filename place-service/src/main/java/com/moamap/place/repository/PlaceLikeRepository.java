package com.moamap.place.repository;

import java.util.Collection;
import java.util.List;
import com.moamap.place.entity.PlaceLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceLikeRepository extends JpaRepository<PlaceLike, Long> {

    boolean existsByPlaceIdAndUserId(Long placeId, Long userId);

    /**
     * 취소. 파생 delete 쿼리는 엔티티를 먼저 읽어와 건별로 지우므로, 한 문장으로 끝나도록 직접 쓴다.
     * 지울 게 없으면 0을 반환한다 — 취소는 몇 번을 해도 같은 결과다.
     */
    @Modifying
    @Query("delete from PlaceLike l where l.placeId = :placeId and l.userId = :userId")
    int deleteLike(@Param("placeId") Long placeId, @Param("userId") Long userId);

    long countByPlaceId(Long placeId);

    /**
     * 여러 장소 중 이 사용자가 하트를 눌러 둔 장소 id만 한 번에 가져온다.
     *
     * 목록 20건에 대해 건별로 물으면 요청당 20쿼리가 붙는다. 페이지 크기와 무관하게 1회로 끝낸다.
     * (MapService.joinedMapIds와 같은 방식)
     */
    @Query("select l.placeId from PlaceLike l where l.userId = :userId and l.placeId in :placeIds")
    List<Long> findLikedPlaceIds(@Param("userId") Long userId, @Param("placeIds") Collection<Long> placeIds);
}
