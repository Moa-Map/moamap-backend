package com.moamap.place.dto;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import com.moamap.place.entity.PlaceActivityType;

public record PlaceActivityResponse(
    PlaceActivityType type,
    LocalDateTime occurredAt,
    Long actorId,
    String actorNickname,
    String actorProfileImageUrl,
    Long placeId,
    String placeName,
    Long reviewId,
    Integer rating
) {

    /**
     * 행 순서: [event_type, occurred_at, actor_id, place_id, place_name, review_id, rating]
     * occurred_at은 Postgres 드라이버가 Timestamp를, H2가 LocalDateTime을 줄 수 있어 둘 다 받는다.
     */
    public static PlaceActivityResponse from(Object[] row) {
        return new PlaceActivityResponse(
            PlaceActivityType.valueOf((String) row[0]),
            toLocalDateTime(row[1]),
            toLong(row[2]),
            null,
            null,
            toLong(row[3]),
            (String) row[4],
            toLong(row[5]),
            row[6] == null ? null : ((Number) row[6]).intValue()
        );
    }

    public PlaceActivityResponse withActorProfile(String actorNickname, String actorProfileImageUrl) {
        return new PlaceActivityResponse(
            type, occurredAt, actorId, actorNickname, actorProfileImageUrl,
            placeId, placeName, reviewId, rating
        );
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
