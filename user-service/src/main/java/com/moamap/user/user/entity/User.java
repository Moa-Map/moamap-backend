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
import jakarta.persistence.Version;
import java.time.Instant;
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

    /** 탈퇴한 회원의 표시 이름. 다른 서비스가 프로필을 조회하면 탈퇴 회원은 빠지지만, 직접 조회되는 경우를 대비한다. */
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // 시각은 UTC 기준 Instant로 저장한다. 응답도 오프셋(...Z) 포함 ISO-8601로 나가 앱이 로컬로 변환한다.
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * 낙관적 락. JPA는 수정 시 모든 컬럼을 다시 쓰므로, 탈퇴 직전에 회원을 읽어 둔 다른 요청(로그인, 프로필 수정)이
     * 탈퇴 뒤에 저장하면 deleted_at과 개인정보를 탈퇴 전 값으로 되돌린다. 버전이 어긋나면 그 저장을 거부한다.
     *
     * 기존 행에 값을 채우려고 기본값을 둔다(ddl-auto: update가 컬럼을 추가할 때 NULL이면 버전 비교가 깨진다).
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private Long version;

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

    public void updateLastLogin(Instant at) {
        this.lastLoginAt = at;
    }

    /**
     * 탈퇴 처리. 행은 남기고 개인을 알아볼 수 있는 값만 지운다(소프트 삭제 + 익명화).
     *
     * 소셜 ID도 지운다. 남겨두면 소셜 회원번호라는 식별 정보가 남고, (provider, provider_id) 유일 제약 때문에
     * 같은 소셜 계정으로 다시 가입할 수 없다. 회원 ID를 넣은 값으로 바꾸므로 유일 제약도 그대로 지켜진다.
     */
    public void withdraw(Instant at) {
        this.nickname = WITHDRAWN_NICKNAME;
        this.email = null;
        this.profileImageUrl = null;
        this.introduction = null;
        this.providerId = "withdrawn:" + id;
        this.deletedAt = at;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    public void updateProfile(String nickname, String profileImageUrl, String email, String introduction) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
        if (email != null) {
            this.email = email;
        }
        if (introduction != null) {
            this.introduction = introduction;
        }
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
