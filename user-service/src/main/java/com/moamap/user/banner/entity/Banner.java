package com.moamap.user.banner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메인 화면 배너. 이미지 한 장이 배너 하나다(문구·버튼은 이미지에 포함).
 *
 * 앱은 display_order 순서대로 넘겨가며 보여준다. 삭제는 소프트 삭제다(팀 규칙).
 */
@Entity
@Table(name = "banner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 등록한 관리자 ID. */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Banner(String imageUrl, int displayOrder, Long createdBy) {
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
        this.createdBy = createdBy;
    }

    public static Banner create(String imageUrl, int displayOrder, Long createdBy) {
        return new Banner(imageUrl, displayOrder, createdBy);
    }

    public void changeImage(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void delete(Instant at) {
        this.deletedAt = at;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
