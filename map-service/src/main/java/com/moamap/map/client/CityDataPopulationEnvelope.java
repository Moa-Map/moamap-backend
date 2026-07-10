package com.moamap.map.client;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 서울 실시간 도시데이터 응답: 루트 키 = "SeoulRtd.citydata_ppltn", 값은 row 배열이 직접 온다
 * (list_total_count/RESULT 래핑 없음 — 문서와 다름, 실제 curl 응답으로 확인).
 */
public record CityDataPopulationEnvelope(
    @JsonProperty("SeoulRtd.citydata_ppltn") List<CityDataPopulationRow> rows
) {
}
