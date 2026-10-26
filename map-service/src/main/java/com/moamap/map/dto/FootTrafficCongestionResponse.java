package com.moamap.map.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.moamap.map.entity.FootTrafficCongestion;

/**
 * 유동인구 관측지점의 최신 혼잡도 스냅샷. 5분마다 폴링하는 쪽이라 정적 정보(이름/좌표)는
 * 아예 안 실어보낸다 — 아직 한 번도 동기화 안 된 지점은 이 목록에 안 나온다(area 목록은 별도 API).
 */
public record FootTrafficCongestionResponse(
    String footTrafficAreaCd,
    String congestLvl,
    String congestMsg,
    Integer ppltnMin,
    Integer ppltnMax,
    BigDecimal maleRate,
    BigDecimal femaleRate,
    BigDecimal ppltnRate0,
    BigDecimal ppltnRate10,
    BigDecimal ppltnRate20,
    BigDecimal ppltnRate30,
    BigDecimal ppltnRate40,
    BigDecimal ppltnRate50,
    BigDecimal ppltnRate60,
    BigDecimal ppltnRate70,
    BigDecimal resntRate,
    BigDecimal nonResntRate,
    LocalDateTime ppltnTime,
    LocalDateTime updatedAt
) {

    public static FootTrafficCongestionResponse from(FootTrafficCongestion congestion) {
        return new FootTrafficCongestionResponse(
            congestion.getFootTrafficAreaCd(),
            congestion.getCongestLvl(),
            congestion.getCongestMsg(),
            congestion.getPpltnMin(),
            congestion.getPpltnMax(),
            congestion.getMaleRate(),
            congestion.getFemaleRate(),
            congestion.getPpltnRate0(),
            congestion.getPpltnRate10(),
            congestion.getPpltnRate20(),
            congestion.getPpltnRate30(),
            congestion.getPpltnRate40(),
            congestion.getPpltnRate50(),
            congestion.getPpltnRate60(),
            congestion.getPpltnRate70(),
            congestion.getResntRate(),
            congestion.getNonResntRate(),
            congestion.getPpltnTime(),
            congestion.getUpdatedAt()
        );
    }
}
