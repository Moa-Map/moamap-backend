package com.moamap.place.dto;

import java.math.BigDecimal;

/**
 * 인스타그램 설명글 → Gemini → 카카오맵 검색으로 찾은 장소 후보.
 * 사용자가 후보 중 하나를 고르면 그대로 장소 등록(PlaceCreateRequest) 요청에 사용할 수 있다.
 */
public record PlaceCandidateResponse(
    String kakaoPlaceId,
    String name,
    String category,
    String address,
    String roadAddress,
    BigDecimal lat,
    BigDecimal lng,
    String placeUrl,
    String sourceUrl
) {
}
