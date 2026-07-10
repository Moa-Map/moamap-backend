package com.moamap.map.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유동인구 관측지점당 1행, 최신 혼잡도 스냅샷. 이력 없이 배치가 매 주기마다 upsert한다.
 */
@Entity
@Table(name = "foot_traffic_congestion")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FootTrafficCongestion {

    @Id
    @Column(name = "foot_traffic_area_cd")
    private String footTrafficAreaCd;

    @Column(name = "congest_lvl", nullable = false)
    private String congestLvl;

    @Column(name = "congest_msg")
    private String congestMsg;

    @Column(name = "ppltn_min")
    private Integer ppltnMin;

    @Column(name = "ppltn_max")
    private Integer ppltnMax;

    @Column(name = "male_rate", precision = 5, scale = 2)
    private BigDecimal maleRate;

    @Column(name = "female_rate", precision = 5, scale = 2)
    private BigDecimal femaleRate;

    @Column(name = "ppltn_rate_0", precision = 5, scale = 2)
    private BigDecimal ppltnRate0;

    @Column(name = "ppltn_rate_10", precision = 5, scale = 2)
    private BigDecimal ppltnRate10;

    @Column(name = "ppltn_rate_20", precision = 5, scale = 2)
    private BigDecimal ppltnRate20;

    @Column(name = "ppltn_rate_30", precision = 5, scale = 2)
    private BigDecimal ppltnRate30;

    @Column(name = "ppltn_rate_40", precision = 5, scale = 2)
    private BigDecimal ppltnRate40;

    @Column(name = "ppltn_rate_50", precision = 5, scale = 2)
    private BigDecimal ppltnRate50;

    @Column(name = "ppltn_rate_60", precision = 5, scale = 2)
    private BigDecimal ppltnRate60;

    @Column(name = "ppltn_rate_70", precision = 5, scale = 2)
    private BigDecimal ppltnRate70;

    @Column(name = "resnt_rate", precision = 5, scale = 2)
    private BigDecimal resntRate;

    @Column(name = "non_resnt_rate", precision = 5, scale = 2)
    private BigDecimal nonResntRate;

    @Column(name = "replace_yn")
    private Boolean replaceYn;

    @Column(name = "ppltn_time")
    private LocalDateTime ppltnTime;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}