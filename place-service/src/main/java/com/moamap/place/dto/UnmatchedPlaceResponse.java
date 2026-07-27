package com.moamap.place.dto;

import java.math.BigDecimal;

/**
 * 카카오 장소와 매칭하지 못해 등록 후보에서 빠진 장소.
 * 조용히 누락시키면 사용자가 "20개 저장했는데 왜 17개지?"의 원인을 알 수 없다.
 */
public record UnmatchedPlaceResponse(
    String name,
    String address,
    BigDecimal lat,
    BigDecimal lng,
    UnmatchReason reason
) {
}
