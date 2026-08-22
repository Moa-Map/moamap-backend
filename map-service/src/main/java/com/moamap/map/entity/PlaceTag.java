package com.moamap.map.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 게시물에 태그된 장소. 이름은 스냅샷이다.
 *
 * places는 place-service DB라 조인할 수 없고, 이름은 프론트가 태그 피커에서 고를 때 함께 보낸 값을
 * 그대로 저장한다. 서버는 검증하지 않는다(이슈 #95 권한 규칙 7).
 */
@Embeddable
public record PlaceTag(
    @Column(name = "place_id", nullable = false) Long placeId,
    @Column(name = "place_name", nullable = false, length = 255) String placeName
) {
    protected PlaceTag() {
        this(null, null);
    }
}
