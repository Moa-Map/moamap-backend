package com.moamap.map.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * 로그 탭 게시물.
 *
 * 사진과 장소 태그는 게시물 없이 존재할 의미가 없고 개별로 지목되지도 않아 @ElementCollection으로 둔다.
 * 피드 목록에서 컬렉션이 게시물마다 따로 조회되지 않게 @BatchSize를 붙인다.
 */
@Entity
@Table(name = "map_posts",
    indexes = @Index(name = "idx_map_posts_map_created", columnList = "map_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_id", nullable = false)
    private Long mapId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ElementCollection
    @CollectionTable(name = "map_post_images", joinColumns = @JoinColumn(name = "map_post_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", length = 1000)
    @BatchSize(size = 20)
    private List<String> imageUrls = new ArrayList<>();

    // @OrderColumn이 없는 컬렉션은 Hibernate가 bag으로 보고 PK를 만들지 않는다.
    // 같은 장소를 두 번 태그하지 못하게 유니크 제약을 직접 건다(태그는 순서가 의미 없어 @OrderColumn을 쓰지 않는다).
    @ElementCollection
    @CollectionTable(name = "map_post_place_tags",
        joinColumns = @JoinColumn(name = "map_post_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_map_post_place_tags_post_place",
            columnNames = {"map_post_id", "place_id"}))
    @BatchSize(size = 20)
    private List<PlaceTag> placeTags = new ArrayList<>();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private MapPost(Long mapId, Long userId, String content, List<String> imageUrls, List<PlaceTag> placeTags) {
        this.mapId = mapId;
        this.userId = userId;
        this.content = content;
        this.imageUrls = new ArrayList<>(imageUrls);
        this.placeTags = new ArrayList<>(placeTags);
    }

    public static MapPost create(Long mapId, Long userId, String content,
            List<String> imageUrls, List<PlaceTag> placeTags) {
        return new MapPost(mapId, userId, content,
            imageUrls == null ? List.of() : imageUrls,
            placeTags == null ? List.of() : placeTags);
    }

    /** 부분 수정. null인 필드는 기존 값을 유지한다. 컬렉션은 전체 교체다. */
    public void update(String content, List<String> imageUrls, List<PlaceTag> placeTags) {
        if (content != null) {
            this.content = content;
        }
        if (imageUrls != null) {
            this.imageUrls.clear();
            this.imageUrls.addAll(imageUrls);
        }
        if (placeTags != null) {
            this.placeTags.clear();
            this.placeTags.addAll(placeTags);
        }
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isWrittenBy(Long userId) {
        return this.userId.equals(userId);
    }
}
