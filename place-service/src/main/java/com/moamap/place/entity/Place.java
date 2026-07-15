package com.moamap.place.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "places")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "kakao_place_id", length = 30)
    private String kakaoPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private PlaceSourceType sourceType;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "map_id", nullable = false)
    private Long mapId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PlaceStatus status = PlaceStatus.PENDING;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;

    public void update(String name, String address, String roadAddress, BigDecimal lat, BigDecimal lng,
            String category, String description) {
        this.name = name;
        this.address = address;
        this.roadAddress = roadAddress;
        this.lat = lat;
        this.lng = lng;
        this.category = category;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public void approve(Long reviewedBy) {
        this.status = PlaceStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Long reviewedBy) {
        this.status = PlaceStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}