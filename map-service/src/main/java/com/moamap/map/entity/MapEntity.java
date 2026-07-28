package com.moamap.map.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지도 방(오픈채팅방처럼 사용자가 만들고 함께 채우는 지도).
 * memberCount는 참여 인원을 매번 집계하지 않도록 비정규화해 관리한다.
 */
@Entity
@Table(
    name = "map_entity",
    indexes = {
        @Index(name = "idx_map_type", columnList = "type"),
        @Index(name = "idx_map_owner", columnList = "owner_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MapType type;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** PRIVATE 지도에서만 발급되는 합류용 초대 코드. */
    @Column(name = "invite_code", length = 12, unique = true)
    private String inviteCode;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    /** place-service의 장소 변경 이벤트로 갱신되는 비정규화 카운트. 목록 노출 대상(APPROVED·미삭제) 개수. */
    @Column(name = "place_count", nullable = false, columnDefinition = "integer not null default 0")
    private int placeCount;

    @ElementCollection
    @CollectionTable(name = "map_tag", joinColumns = @JoinColumn(name = "map_id"))
    @Column(name = "tag", length = 30)
    private List<String> tags = new ArrayList<>();

    private MapEntity(String name, String description, String imageUrl, MapType type,
                      Long ownerId, List<String> tags, String inviteCode) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.type = type;
        this.ownerId = ownerId;
        this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
        this.inviteCode = inviteCode;
        this.memberCount = 1; // 생성자(OWNER) 포함
    }

    public static MapEntity create(String name, String description, String imageUrl, MapType type,
                                   Long ownerId, List<String> tags, String inviteCode) {
        return new MapEntity(name, description, imageUrl, type, ownerId, tags, inviteCode);
    }

    public void update(String name, String description, String imageUrl, List<String> tags) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public void increaseMemberCount() {
        this.memberCount++;
    }

    public void decreaseMemberCount() {
        if (this.memberCount > 0) {
            this.memberCount--;
        }
    }

    public boolean isPrivate() {
        return this.type == MapType.PRIVATE;
    }

    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }
}
