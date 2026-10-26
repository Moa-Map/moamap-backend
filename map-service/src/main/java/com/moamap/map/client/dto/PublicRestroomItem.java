package com.moamap.map.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 원천 필드명 그대로(@JsonProperty) 받는다. 전부 String — 원천이 숫자를 문자열로 내려주는 경우가 있어 파싱은 상위 계층에서 한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicRestroomItem(
    @JsonProperty("MNG_NO") String mngNo,
    @JsonProperty("RSTRM_NM") String name,
    @JsonProperty("SE_NM") String category,
    @JsonProperty("RSTRM_PSN_SE_NM") String ownerType,
    @JsonProperty("LCTN_ROAD_NM_ADDR") String roadAddress,
    @JsonProperty("LCTN_LOTNO_ADDR") String lotAddress,
    @JsonProperty("MALE_TOILT_CNT") String maleToilet,
    @JsonProperty("MALE_URNL_CNT") String maleUrinal,
    @JsonProperty("MALE_FRDBL_TOILT_CNT") String maleDisabledToilet,
    @JsonProperty("MALE_FRDBL_URNL_CNT") String maleDisabledUrinal,
    @JsonProperty("MALE_CHLD_TOILT_CNT") String maleChildToilet,
    @JsonProperty("MALE_CHLD_URNL_CNT") String maleChildUrinal,
    @JsonProperty("FEMALE_TOILT_CNT") String femaleToilet,
    @JsonProperty("FEMALE_FRDBL_TOILT_CNT") String femaleDisabledToilet,
    @JsonProperty("FEMALE_CHLD_TOILT_CNT") String femaleChildToilet,
    @JsonProperty("OPN_HR") String openHours,
    @JsonProperty("OPN_HR_DTL") String openHoursDetail,
    @JsonProperty("DIAP_EXCHCON_EN") String diaperTable,
    @JsonProperty("DIAP_EXCHCON_PLC") String diaperTableLocation,
    @JsonProperty("EMRGNCBLL_INSTL_YN") String emergencyBell,
    @JsonProperty("EMRGNCBLL_INSTL_PLC") String emergencyBellLocation,
    @JsonProperty("RSTRM_ENTRAN_CCTV_INSTL_EN") String entranceCctv,
    @JsonProperty("WSTE_PRCS_MTH_NM") String wasteDisposal,
    @JsonProperty("MNG_INST_NM") String managerOrg,
    @JsonProperty("TELNO") String phone,
    @JsonProperty("INSTL_YM") String installedYm,
    @JsonProperty("RMOD_YM") String remodeledYm,
    @JsonProperty("LAST_MDFCN_PNT") String lastModifiedAt,
    @JsonProperty("DAT_CRTR_YMD") String dataRefDate
) {}
