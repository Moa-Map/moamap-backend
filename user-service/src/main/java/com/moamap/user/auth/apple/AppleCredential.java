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

    public AppleCredential(User user, String clientId, AppleTokenCipher.EncryptedToken token) {
        this.user = user;
        this.clientId = clientId;
        replace(token);
    }

    public void replace(AppleTokenCipher.EncryptedToken token) {
        this.encryptedRefreshToken = token.ciphertext();
        this.keyVersion = token.keyVersion();
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
