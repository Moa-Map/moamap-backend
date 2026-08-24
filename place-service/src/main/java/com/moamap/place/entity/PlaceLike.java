package com.moamap.place.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소에 누른 하트. 한 사람이 한 장소에 하나만 가질 수 있다.
 *
 * 취소는 행을 지우는 것이라 소프트 삭제를 두지 않는다 — 누가 언제 취소했는지는 쓸 데가 없고,
 * 남겨두면 "누른 적 있는지"와 "지금 눌러 둔 상태인지"를 구분하는 조건이 모든 조회에 붙는다.
 *
 * user_id 인덱스는 지금 쓰지 않는다. "내가 하트한 장소" 목록이 곧 필요해질 것이라 미리 깔아둔다.
 */
@Entity
@Table(
    name = "place_likes",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_place_likes_place_user",
        columnNames = {"place_id", "user_id"}),
    indexes = @Index(name = "idx_place_likes_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private PlaceLike(Long placeId, Long userId) {
        this.placeId = placeId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }

    public static PlaceLike of(Long placeId, Long userId) {
        return new PlaceLike(placeId, userId);
    }
}
