package com.moamap.map.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그 탭 게시물의 댓글.
 *
 * 게시물과 별개로 페이징 조회하므로 @ManyToOne을 두지 않고 mapPostId만 들고 있다.
 * 물리 FK는 Flyway 이관 시 MapMember.mapId와 함께 추가한다(이슈 #95).
 */
@Entity
@Table(name = "map_post_comments",
    indexes = @Index(name = "idx_map_post_comments_post_created", columnList = "map_post_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapPostComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_post_id", nullable = false)
    private Long mapPostId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private MapPostComment(Long mapPostId, Long userId, String content) {
        this.mapPostId = mapPostId;
        this.userId = userId;
        this.content = content;
    }

    public static MapPostComment create(Long mapPostId, Long userId, String content) {
        return new MapPostComment(mapPostId, userId, content);
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isWrittenBy(Long userId) {
        return this.userId.equals(userId);
    }
}
