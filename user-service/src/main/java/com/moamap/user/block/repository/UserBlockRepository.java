package com.moamap.user.block.repository;

import com.moamap.user.block.entity.UserBlock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /** 최근 차단한 순. 차단 목록 화면에 쓴다. */
    List<UserBlock> findByBlockerIdOrderByCreatedAtDescIdDesc(Long blockerId);

    /** 내가 차단한 사람 ID. 다른 서비스가 목록에서 이 사람들의 콘텐츠를 뺀다. */
    @Query("select b.blockedId from UserBlock b where b.blockerId = :blockerId")
    List<Long> findBlockedIdsByBlockerId(@Param("blockerId") Long blockerId);

    @Modifying
    @Query("delete from UserBlock b where b.blockerId = :blockerId and b.blockedId = :blockedId")
    int deleteByBlockerIdAndBlockedId(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** 회원 탈퇴 정리용. 그 회원이 한 차단과 받은 차단을 모두 지운다. */
    @Modifying
    @Query("delete from UserBlock b where b.blockerId = :userId or b.blockedId = :userId")
    int deleteAllInvolving(@Param("userId") Long userId);
}
