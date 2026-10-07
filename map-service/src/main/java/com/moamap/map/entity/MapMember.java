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

    /**
     * 모음 화면에서 사용자가 직접 지정한 노출 순서(0부터). 같은 지도라도 사용자마다 다르므로 지도가 아닌 멤버십에 둔다.
     *
     * 한 번도 순서를 바꾼 적 없으면 null이다. 이때는 조회 쿼리가 최신순으로 정렬한다(NULLS LAST).
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

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

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
