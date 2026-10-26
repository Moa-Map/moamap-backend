package com.moamap.user.inquiry.repository;

import com.moamap.user.inquiry.entity.Inquiry;
import com.moamap.user.inquiry.entity.InquiryStatus;
import com.moamap.user.inquiry.entity.InquiryType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    /** 도배 방지용. 최근 기간 안에 이 회원이 등록한 문의 수. */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    /** 관리자 목록. 상태·유형은 넘어온 것만 조건으로 건다. */
    @Query("select i from Inquiry i "
            + "where (:status is null or i.status = :status) and (:type is null or i.type = :type)")
    Page<Inquiry> search(@Param("status") InquiryStatus status, @Param("type") InquiryType type, Pageable pageable);

    /** 회원 탈퇴 시 그 회원의 문의를 지운다. 이메일·본문 같은 개인정보를 남기지 않기 위해 행째 지운다. */
    @Modifying
    @Query("delete from Inquiry i where i.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
