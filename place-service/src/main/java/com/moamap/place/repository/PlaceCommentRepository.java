package com.moamap.place.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.moamap.place.entity.PlaceComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceCommentRepository extends JpaRepository<PlaceComment, Long> {

    Optional<PlaceComment> findByIdAndDeletedAtIsNull(Long id);

    Page<PlaceComment> findByPlaceIdAndDeletedAtIsNull(Long placeId, Pageable pageable);

    /** 요청한 사람이 차단한 사용자의 댓글을 뺀 목록. */
    Page<PlaceComment> findByPlaceIdAndDeletedAtIsNullAndUserIdNotIn(Long placeId, Collection<Long> blockedUserIds,
        Pageable pageable);

    long countByPlaceIdAndDeletedAtIsNull(Long placeId);

    // JPA의 avg()는 스펙상 항상 Double을 반환한다. BigDecimal로 선언하면 런타임에 타입 캐스팅 오류가 난다.
    @Query("select coalesce(avg(r.rating), 0) from PlaceComment r where r.placeId = :placeId and r.deletedAt is null")
    Double averageRatingByPlaceId(@Param("placeId") Long placeId);

    /**
     * 신고 수를 1 올린다.
     *
     * 댓글 요약값(avgRating/commentCount)과 달리 재계산이 아니라 증분이다. 신고 행은 삭제되지 않아
     * 증분만으로 값이 어긋나지 않고, DB가 더하기를 수행하므로 동시 신고에도 유실이 없다.
     */
    @Modifying
    @Query("update PlaceComment c set c.reportCount = c.reportCount + 1 where c.id = :commentId")
    void increaseReportCount(@Param("commentId") Long commentId);

    /** 탈퇴 정리용. 댓글을 지운 뒤 댓글 수·평점을 다시 계산할 장소들이다. */
    @Query("select distinct c.placeId from PlaceComment c where c.userId = :userId and c.deletedAt is null")
    List<Long> findPlaceIdsOfActiveCommentsByUserId(@Param("userId") Long userId);

    /** 작성자가 직접 지울 때와 같은 소프트 삭제다. 신고 기록은 댓글을 가리킨 채로 남는다. */
    @Modifying
    @Query("update PlaceComment c set c.deletedAt = :deletedAt where c.userId = :userId and c.deletedAt is null")
    int softDeleteAllByUserId(@Param("userId") Long userId, @Param("deletedAt") LocalDateTime deletedAt);
}
