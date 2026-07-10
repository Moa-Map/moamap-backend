package com.moamap.user.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_users_provider",
                columnNames = {"provider", "provider_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private User(String provider, String providerId, String nickname,
                 String email, String profileImageUrl) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.role = Role.USER;
    }

    public static User createSocialUser(String provider, String providerId, String nickname,
                                        String email, String profileImageUrl) {
        return new User(provider, providerId, nickname, email, profileImageUrl);
    }

    public void updateLastLogin(LocalDateTime at) {
        this.lastLoginAt = at;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
