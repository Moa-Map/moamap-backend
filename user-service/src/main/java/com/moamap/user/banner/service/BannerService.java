package com.moamap.user.banner.service;

import com.moamap.user.banner.dto.BannerResponse;
import com.moamap.user.banner.repository.BannerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 메인 배너 조회. 로그인 없이 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerService {

    private final BannerRepository bannerRepository;

    public List<BannerResponse> getBanners() {
        return bannerRepository.findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc().stream()
                .map(BannerResponse::from)
                .toList();
    }
}
