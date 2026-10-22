package com.moamap.user.term.repository;

import com.moamap.user.term.entity.UserTerm;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTermRepository extends JpaRepository<UserTerm, Long> {

    @Query("select ut.termId from UserTerm ut where ut.userId = :userId")
    Set<Long> findTermIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from UserTerm ut where ut.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
