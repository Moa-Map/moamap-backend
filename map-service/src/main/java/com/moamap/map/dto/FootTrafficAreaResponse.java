package com.moamap.map.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.moamap.map.entity.FootTrafficArea;

/**
 * 유동인구 관측지점 정적 정보 (이름/카테고리/좌표/구역 경계). 거의 안 바뀌는 데이터라
 * 혼잡도(5분마다 갱신)와 분리해서, 프론트가 앱 시작 시 한 번만 받으면 되게 한다.
 *
 * boundary(폴리곤)를 여기 같이 포함한다 — 지도에 121개 구역을 전부 그려야 해서, 개별로
 * 121번 조회하는 것보다 한 번에 묶어서 받는 게 훨씬 적은 요청 수로 끝난다.
 */
public record FootTrafficAreaResponse(
    String footTrafficAreaCd,
    String areaNm,
    String engNm,
    String category,
    BigDecimal lat,
    BigDecimal lng,
    @JsonRawValue String boundary
) {

    public static FootTrafficAreaResponse from(FootTrafficArea area) {
        return new FootTrafficAreaResponse(
            area.getFootTrafficAreaCd(),
            area.getAreaNm(),
            area.getEngNm(),
            area.getCategory(),
            area.getLat(),
            area.getLng(),
            area.getBoundary()
        );
    }
}
