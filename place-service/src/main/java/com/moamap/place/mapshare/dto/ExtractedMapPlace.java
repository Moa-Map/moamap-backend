package com.moamap.place.mapshare.dto;

import java.math.BigDecimal;

/**
 * 3사 공유 리스트에서 뽑아낸 장소 1건. 카카오 재매칭 전의 원본 값이다.
 *
 * category는 네이버만 채워진다(mcidName). 카카오·구글 리스트 응답에는 분류가 없다.
 * placeId는 각 서비스의 자체 ID이며 kakaoPlaceId가 아니다.
 */
public record ExtractedMapPlace(
    String name,
    String address,
    String category,
    BigDecimal lat,
    BigDecimal lng,
    String placeId,
    String memo
) {
}
