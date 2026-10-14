package com.moamap.user.banner.repository;

import com.moamap.user.banner.entity.Banner;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    /** 노출 순서대로. 순서가 같으면 먼저 등록한 것이 앞이다. */
    List<Banner> findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc();

    Optional<Banner> findByIdAndDeletedAtIsNull(Long id);

    long countByDeletedAtIsNull();

    /** 새 배너를 맨 뒤에 붙이기 위한 현재 최대 순서. 배너가 없으면 -1. */
    @Query("select coalesce(max(b.displayOrder), -1) from Banner b where b.deletedAt is null")
    int findMaxDisplayOrder();
}
