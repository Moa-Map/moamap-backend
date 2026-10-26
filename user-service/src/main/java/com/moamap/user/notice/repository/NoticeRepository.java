package com.moamap.user.notice.repository;

import com.moamap.user.notice.entity.Notice;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findByDeletedAtIsNull(Pageable pageable);

    Optional<Notice> findByIdAndDeletedAtIsNull(Long id);
}
