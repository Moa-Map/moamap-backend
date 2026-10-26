package com.moamap.place.repository;

import com.moamap.place.entity.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 별도 로그 테이블 없이 places/place_reviews 컬럼에서 이벤트를 도출해 UNION ALL로 병합한다.
 */
public interface PlaceActivityRepository extends Repository<Place, Long> {

    @Query(value = """
            select 'PLACE_ADDED' as event_type, p.created_at as occurred_at, p.created_by as actor_id,
                   p.id as place_id, p.name as place_name,
                   cast(null as bigint) as review_id, cast(null as integer) as rating
              from {h-schema}places p
             where p.map_id = :mapId and p.status = 'APPROVED'
            union all
            select 'PLACE_DELETED', p.deleted_at, p.deleted_by, p.id, p.name, null, null
              from {h-schema}places p
             where p.map_id = :mapId and p.status = 'APPROVED' and p.deleted_at is not null
            union all
            select 'REVIEW_CREATED', r.created_at, r.user_id, p.id, p.name, r.id, r.rating
              from {h-schema}place_reviews r
              join {h-schema}places p on p.id = r.place_id
             where p.map_id = :mapId and p.status = 'APPROVED'
               and p.deleted_at is null and r.deleted_at is null
               and :includeReviews = true
             order by occurred_at desc, event_type asc, place_id desc, review_id desc nulls last
            """,
        countQuery = """
            select count(*) from (
                select p.id as place_id
                  from {h-schema}places p
                 where p.map_id = :mapId and p.status = 'APPROVED'
                union all
                select p.id
                  from {h-schema}places p
                 where p.map_id = :mapId and p.status = 'APPROVED' and p.deleted_at is not null
                union all
                select p.id
                  from {h-schema}place_reviews r
                  join {h-schema}places p on p.id = r.place_id
                 where p.map_id = :mapId and p.status = 'APPROVED'
                   and p.deleted_at is null and r.deleted_at is null
                   and :includeReviews = true
            ) t
            """,
        nativeQuery = true)
    Page<Object[]> findActivities(@Param("mapId") Long mapId,
                                   @Param("includeReviews") boolean includeReviews,
                                   Pageable pageable);
}
