package com.moamap.user.banner.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.user.banner.dto.AdminBannerResponse;
import com.moamap.user.banner.dto.BannerImageUploadUrlRequest;
import com.moamap.user.banner.dto.BannerImageUploadUrlResponse;
import com.moamap.user.banner.entity.Banner;
import com.moamap.user.banner.repository.BannerRepository;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.user.service.AdminGuard;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 배너 관리.
 *
 * 권한 확인을 가장 먼저 한다. 관리자가 아니면 없는 배너 ID로 요청해도 403이라 존재 여부를 알 수 없다.
 * 배너 수 제한(10개)은 등록할 때 센다. 관리자 두 명이 동시에 등록하면 하나 넘칠 수 있지만, 관리자만 쓰는 기능이라 감수한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BannerAdminService {

    static final int MAX_BANNERS = 10;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long UPLOAD_URL_EXPIRES_IN_SECONDS = 300L;

    private final BannerRepository bannerRepository;
    private final AdminGuard adminGuard;
    private final BannerImageUrlPolicy imageUrlPolicy;
    private final ObjectProvider<ObjectStoragePresigner> presignerProvider;

    @Transactional(readOnly = true)
    public BannerImageUploadUrlResponse issueImageUploadUrl(Long adminId, BannerImageUploadUrlRequest request) {
        adminGuard.requireAdmin(adminId);
        String contentType = request.contentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(UserErrorCode.INVALID_FILE_TYPE);
        }
        if (request.fileSize() > MAX_FILE_SIZE) {
            throw new BusinessException(UserErrorCode.FILE_SIZE_EXCEEDED);
        }
        ObjectStoragePresigner presigner = presignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new BusinessException(UserErrorCode.STORAGE_NOT_CONFIGURED);
        }
        return BannerImageUploadUrlResponse.from(
                presigner.presign(BannerImageUrlPolicy.KEY_PREFIX, contentType, UPLOAD_URL_EXPIRES_IN_SECONDS));
    }

    @Transactional(readOnly = true)
    public List<AdminBannerResponse> getBanners(Long adminId) {
        adminGuard.requireAdmin(adminId);
        return activeBanners().stream().map(AdminBannerResponse::from).toList();
    }

    /** 새 배너는 맨 뒤에 붙는다. */
    public AdminBannerResponse create(Long adminId, String imageUrl) {
        adminGuard.requireAdmin(adminId);
        String verified = imageUrlPolicy.requireBannerImage(imageUrl);
        if (bannerRepository.countByDeletedAtIsNull() >= MAX_BANNERS) {
            throw new BusinessException(UserErrorCode.BANNER_LIMIT_EXCEEDED);
        }
        int lastOrder = bannerRepository.findMaxDisplayOrder();
        Banner banner = bannerRepository.save(Banner.create(verified, lastOrder + 1, adminId));
        return AdminBannerResponse.from(banner);
    }

    public AdminBannerResponse changeImage(Long adminId, Long bannerId, String imageUrl) {
        adminGuard.requireAdmin(adminId);
        String verified = imageUrlPolicy.requireBannerImage(imageUrl);
        Banner banner = getActiveBanner(bannerId);
        banner.changeImage(verified);
        // 응답에 바뀐 수정 시각을 담으려면 지금 반영해야 한다(@PreUpdate는 flush 때 실행된다).
        bannerRepository.flush();
        return AdminBannerResponse.from(banner);
    }

    /**
     * 보낸 순서대로 0부터 다시 매긴다. 등록된 배너와 정확히 일치할 때만 반영한다.
     * 하나라도 빠지거나 남거나 중복되면 전체를 거부한다 — 일부만 반영되면 관리자 화면과 실제 순서가 어긋난다.
     */
    public List<AdminBannerResponse> reorder(Long adminId, List<Long> bannerIds) {
        adminGuard.requireAdmin(adminId);
        Map<Long, Banner> banners = activeBanners().stream()
                .collect(Collectors.toMap(Banner::getId, Function.identity()));
        Set<Long> requested = Set.copyOf(bannerIds);
        if (requested.size() != bannerIds.size() || !requested.equals(banners.keySet())) {
            throw new BusinessException(UserErrorCode.INVALID_BANNER_ORDER);
        }
        for (int order = 0; order < bannerIds.size(); order++) {
            banners.get(bannerIds.get(order)).changeDisplayOrder(order);
        }
        bannerRepository.flush();
        return activeBanners().stream().map(AdminBannerResponse::from).toList();
    }

    public void delete(Long adminId, Long bannerId) {
        adminGuard.requireAdmin(adminId);
        getActiveBanner(bannerId).delete(Instant.now());
    }

    private List<Banner> activeBanners() {
        return bannerRepository.findByDeletedAtIsNullOrderByDisplayOrderAscIdAsc();
    }

    private Banner getActiveBanner(Long bannerId) {
        return bannerRepository.findByIdAndDeletedAtIsNull(bannerId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.BANNER_NOT_FOUND));
    }
}
