package com.moamap.place.dto;

import java.math.BigDecimal;
import com.moamap.place.entity.PlaceSourceType;

/**
 * 지도 공유 링크에서 추출해 카카오 장소와 매칭된 후보.
 * mapId만 더하면 그대로 일괄 등록 요청 항목이 된다.
 *
 * 인스타그램 추출의 PlaceCandidateResponse와는 별개 타입이다. 두 기능은 서로 무관하다.
 */
public record MapSharePlaceCandidate(
    String kakaoPlaceId,
    String name,
    String category,
    String address,
    String roadAddress,
    BigDecimal lat,
    BigDecimal lng,
    String placeUrl,
    String description,
    String sourceUrl,
    PlaceSourceType sourceType
) {
}
