package com.moamap.user.user.repository;

import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    List<User> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);

    /** 운영자(관리자) 계정 ID. 신고 알림을 받을 사람이다. */
    @Query("select u.id from User u where u.role = :role and u.deletedAt is null")
    List<Long> findIdsByRole(@Param("role") Role role);
}
