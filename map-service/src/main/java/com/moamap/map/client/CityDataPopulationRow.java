package com.moamap.map.client;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * citydata_ppltn 응답의 row 하나. FCST_* (예측) 필드는 지금은 쓰지 않아 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CityDataPopulationRow(
    @JsonProperty("AREA_NM") String areaNm,
    @JsonProperty("AREA_CD") String areaCd,
    @JsonProperty("AREA_CONGEST_LVL") String areaCongestLvl,
    @JsonProperty("AREA_CONGEST_MSG") String areaCongestMsg,
    @JsonProperty("AREA_PPLTN_MIN") Integer areaPpltnMin,
    @JsonProperty("AREA_PPLTN_MAX") Integer areaPpltnMax,
    @JsonProperty("MALE_PPLTN_RATE") BigDecimal malePpltnRate,
    @JsonProperty("FEMALE_PPLTN_RATE") BigDecimal femalePpltnRate,
    @JsonProperty("PPLTN_RATE_0") BigDecimal ppltnRate0,
    @JsonProperty("PPLTN_RATE_10") BigDecimal ppltnRate10,
    @JsonProperty("PPLTN_RATE_20") BigDecimal ppltnRate20,
    @JsonProperty("PPLTN_RATE_30") BigDecimal ppltnRate30,
    @JsonProperty("PPLTN_RATE_40") BigDecimal ppltnRate40,
    @JsonProperty("PPLTN_RATE_50") BigDecimal ppltnRate50,
    @JsonProperty("PPLTN_RATE_60") BigDecimal ppltnRate60,
    @JsonProperty("PPLTN_RATE_70") BigDecimal ppltnRate70,
    @JsonProperty("RESNT_PPLTN_RATE") BigDecimal resntPpltnRate,
    @JsonProperty("NON_RESNT_PPLTN_RATE") BigDecimal nonResntPpltnRate,
    @JsonProperty("REPLACE_YN") String replaceYn,
    @JsonProperty("PPLTN_TIME") String ppltnTime
) {
}