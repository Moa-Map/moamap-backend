package com.moamap.map.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 서울시 실시간 도시데이터 인구 API 대상 유동인구 관측지점 121곳. seed 데이터로만 채워지는 읽기 전용 참조 엔티티.
 * 좌표/구역 경계는 API가 안 주는 값이라 서울시 "주요 121장소 영역" shapefile로 별도 보강한다.
 */
@Entity
@Table(name = "foot_traffic_area")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FootTrafficArea {

    @Id
    @Column(name = "foot_traffic_area_cd")
    private String footTrafficAreaCd;

    @Column(name = "area_nm", nullable = false)
    private String areaNm;

    @Column(name = "eng_nm")
    private String engNm;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "boundary")
    private String boundary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}