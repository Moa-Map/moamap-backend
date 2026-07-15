package com.moamap.map.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 지도 참여 멤버. (map_id, user_id) 조합은 유일하다.
 */
@Entity
@Table(
    name = "map_member",
    uniqueConstraints = @UniqueConstraint(name = "uk_map_member", columnNames = {"map_id", "user_id"}),
    indexes = @Index(name = "idx_member_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_id", nullable = false)
    private Long mapId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MapRole role;

    private MapMember(Long mapId, Long userId, MapRole role) {
        this.mapId = mapId;
        this.userId = userId;
        this.role = role;
    }

    public static MapMember of(Long mapId, Long userId, MapRole role) {
        return new MapMember(mapId, userId, role);
    }

    public void changeRole(MapRole role) {
        this.role = role;
    }
}
