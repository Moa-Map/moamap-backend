package com.moamap.map.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/**
 * 행안부 공공화장실 전량 동기화 결과. 사용자 등록 장소(place)와 달리 소유자·리뷰 개념이 없는
 * 읽기 전용 참조 데이터이며, mng_no를 자연키로 삼아 월 1회 upsert된다.
 */
@Entity
@Table(name = "public_restroom")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PublicRestroom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mng_no", nullable = false, unique = true)
    private String mngNo;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "owner_type")
    private String ownerType;

    @Column(name = "road_address")
    private String roadAddress;

    @Column(name = "lot_address")
    private String lotAddress;

    @Column(name = "male_toilet", nullable = false)
    private Short maleToilet;

    @Column(name = "male_urinal", nullable = false)
    private Short maleUrinal;

    @Column(name = "male_disabled_toilet", nullable = false)
    private Short maleDisabledToilet;

    @Column(name = "male_disabled_urinal", nullable = false)
    private Short maleDisabledUrinal;

    @Column(name = "male_child_toilet", nullable = false)
    private Short maleChildToilet;

    @Column(name = "male_child_urinal", nullable = false)
    private Short maleChildUrinal;

    @Column(name = "female_toilet", nullable = false)
    private Short femaleToilet;

    @Column(name = "female_disabled_toilet", nullable = false)
    private Short femaleDisabledToilet;

    @Column(name = "female_child_toilet", nullable = false)
    private Short femaleChildToilet;

    @Column(name = "open_hours")
    private String openHours;

    @Column(name = "open_hours_detail")
    private String openHoursDetail;

    @Column(name = "diaper_table", nullable = false)
    private boolean diaperTable;

    @Column(name = "diaper_table_location")
    private String diaperTableLocation;

    @Column(name = "emergency_bell", nullable = false)
    private boolean emergencyBell;

    @Column(name = "emergency_bell_location")
    private String emergencyBellLocation;

    @Column(name = "entrance_cctv", nullable = false)
    private boolean entranceCctv;

    @Column(name = "waste_disposal")
    private String wasteDisposal;

    @Column(name = "manager_org")
    private String managerOrg;

    @Column(name = "phone")
    private String phone;

    @Column(name = "installed_ym")
    private String installedYm;

    @Column(name = "remodeled_ym")
    private String remodeledYm;

    @Column(name = "source_modified_at")
    private LocalDateTime sourceModifiedAt;

    @Column(name = "data_ref_date")
    private LocalDate dataRefDate;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Enumerated(EnumType.STRING)
    @Column(name = "geocode_status", nullable = false, length = 10)
    private GeocodeStatus geocodeStatus;

    // 원천 주소가 틀려 좌표를 믿을 수 없는 행을 운영자가 SQL로 숨긴다. 원천 주소가 바뀌면 동기화가 자동으로 푼다.
    // ColumnDefault — ddl-auto: update가 이미 행이 있는 테이블에 NOT NULL 컬럼을 추가할 수 있게 한다.
    @ColumnDefault("false")
    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
