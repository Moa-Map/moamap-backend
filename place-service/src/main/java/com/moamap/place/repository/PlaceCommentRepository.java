package com.moamap.place.repository;

import java.util.Optional;
import com.moamap.place.dto.ReportedCommentResponse;
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

    /**
     * 지도에 속한 댓글 중 신고가 하나라도 쌓인 것들. 많이 신고된 순으로 준다.
     *
     * PlaceComment는 Place를 연관으로 들고 있지 않고 placeId만 가지므로 on 절로 직접 조인한다.
     * 생성자 표현식을 쓰면 Spring Data가 count 쿼리를 유도하지 못하는 경우가 있어 명시한다.
     */
    @Query(value = """
        select new com.moamap.place.dto.ReportedCommentResponse(
            c.id, c.placeId, p.name, c.userId, c.content, c.rating, c.reportCount, c.createdAt)
        from PlaceComment c
        join Place p on p.id = c.placeId
        where p.mapId = :mapId
          and c.deletedAt is null
          and c.reportCount > 0
        order by c.reportCount desc, c.createdAt asc
        """,
        countQuery = """
        select count(c)
        from PlaceComment c
        join Place p on p.id = c.placeId
        where p.mapId = :mapId
          and c.deletedAt is null
          and c.reportCount > 0
        """)
    Page<ReportedCommentResponse> findReportedByMapId(@Param("mapId") Long mapId, Pageable pageable);
}
