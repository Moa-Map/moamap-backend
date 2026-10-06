package com.moamap.user.auth.apple;

import com.moamap.user.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "apple_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppleCredential {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "encrypted_refresh_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "key_version", nullable = false, length = 30)
    private String keyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 탈퇴로 폐기를 요청한 시각. 값이 있으면 폐기 대기 중이고, Apple 폐기가 성공하면 행을 지운다. */
    @Column(name = "revoke_requested_at")
    private Instant revokeRequestedAt;

    /** 폐기에 실패한 횟수. 기존 행을 채우려고 기본값을 둔다(ddl-auto: update). */
    @Column(name = "revoke_attempts", nullable = false, columnDefinition = "integer not null default 0")
    private int revokeAttempts;

    @Column(name = "next_revoke_at")
    private Instant nextRevokeAt;

    public AppleCredential(User user, String clientId, AppleTokenCipher.EncryptedToken token) {
        this.user = user;
        this.clientId = clientId;
        replace(token);
    }

    public void replace(AppleTokenCipher.EncryptedToken token) {
        this.encryptedRefreshToken = token.ciphertext();
        this.keyVersion = token.keyVersion();
    }

    public void requestRevoke(Instant at) {
        this.revokeRequestedAt = at;
        this.nextRevokeAt = at;
    }

    public void recordRevokeFailure(Instant nextAttemptAt) {
        this.revokeAttempts++;
        this.nextRevokeAt = nextAttemptAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
