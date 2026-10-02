package com.moamap.user.auth.apple;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppleCredentialRepository extends JpaRepository<AppleCredential, Long> {
}
