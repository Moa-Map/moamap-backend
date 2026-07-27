package com.moamap.place.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "places",
    uniqueConstraints = @UniqueConstraint(name = "uk_places_map_kakao_place", columnNames = {"map_id", "kakao_place_id"}),
    indexes = @Index(name = "idx_places_map_status_created", columnList = "map_id, status, created_at"))
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

    @Column(name = "lat", precision = 9, scale = 6, nullable = false)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6, nullable = false)
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

    @ElementCollection
    @CollectionTable(name = "place_tag", joinColumns = @JoinColumn(name = "place_id"))
    @Column(name = "tag", length = 30)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;

    /**
     * 부분 수정. null인 필드는 기존 값을 유지하고, 넘어온 필드만 바꾼다.
     */
    public void update(String name, String address, String roadAddress, BigDecimal lat, BigDecimal lng,
            String category, String description, List<String> tags) {
        if (name != null) {
            this.name = name;
        }
        if (address != null) {
            this.address = address;
        }
        if (roadAddress != null) {
            this.roadAddress = roadAddress;
        }
        if (lat != null) {
            this.lat = lat;
        }
        if (lng != null) {
            this.lng = lng;
        }
        if (category != null) {
            this.category = category;
        }
        if (description != null) {
            this.description = description;
        }
        if (tags != null) {
            this.tags.clear();
            this.tags.addAll(tags);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void approve(Long processedBy) {
        this.status = PlaceStatus.APPROVED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
    }

    public void reject(Long processedBy) {
        this.status = PlaceStatus.REJECTED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
    }

    public void delete(Long deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
        // uk_places_map_kakao_place는 deleted_at을 구분하지 않는다.
        // kakaoPlaceId를 비워서 유니크 제약 대상에서 빼야, 같은 지도에 같은 장소를 다시 등록할 수 있다.
        this.kakaoPlaceId = null;
    }
}