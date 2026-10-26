package com.moamap.map.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.moamap.map.entity.FootTrafficCongestion;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 서울시 citydata_ppltn 클라이언트. 이 API는 장소 하나당 호출 하나 —
 * 121곳을 순회하는 배치 쪽에서 이 fetch()를 반복 호출한다.
 */
@Component
public class SeoulCityDataClient {

    private static final DateTimeFormatter PPLTN_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int START_INDEX = 1;
    private static final int END_INDEX = 5;

    private final RestClient restClient;
    private final SeoulOpenApiProperties properties;

    public SeoulCityDataClient(RestClient.Builder builder, SeoulOpenApiProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    public FootTrafficCongestion fetch(String footTrafficAreaCd) {
        CityDataPopulationEnvelope envelope = restClient.get()
            .uri("/{key}/json/{service}/{start}/{end}/{areaCd}",
                properties.key(), properties.service(), START_INDEX, END_INDEX, footTrafficAreaCd)
            .retrieve()
            .body(CityDataPopulationEnvelope.class);

        CityDataPopulationRow row = firstRow(envelope, footTrafficAreaCd);
        return FootTrafficCongestion.builder()
            .footTrafficAreaCd(footTrafficAreaCd)
            .congestLvl(row.areaCongestLvl())
            .congestMsg(row.areaCongestMsg())
            .ppltnMin(row.areaPpltnMin())
            .ppltnMax(row.areaPpltnMax())
            .maleRate(row.malePpltnRate())
            .femaleRate(row.femalePpltnRate())
            .ppltnRate0(row.ppltnRate0())
            .ppltnRate10(row.ppltnRate10())
            .ppltnRate20(row.ppltnRate20())
            .ppltnRate30(row.ppltnRate30())
            .ppltnRate40(row.ppltnRate40())
            .ppltnRate50(row.ppltnRate50())
            .ppltnRate60(row.ppltnRate60())
            .ppltnRate70(row.ppltnRate70())
            .resntRate(row.resntPpltnRate())
            .nonResntRate(row.nonResntPpltnRate())
            .replaceYn("Y".equalsIgnoreCase(row.replaceYn()))
            .ppltnTime(parseTime(row.ppltnTime()))
            .build();
    }

    private CityDataPopulationRow firstRow(CityDataPopulationEnvelope envelope, String footTrafficAreaCd) {
        List<CityDataPopulationRow> rows = envelope == null ? null : envelope.rows();
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("citydata_ppltn 응답에 데이터가 없습니다: " + footTrafficAreaCd);
        }
        return rows.get(0);
    }

    private LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(raw, PPLTN_TIME_FORMAT);
    }
}
