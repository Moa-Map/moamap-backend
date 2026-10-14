package com.moamap.user.banner.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.config.ObjectStorageProperties;
import com.moamap.user.exception.UserErrorCode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 배너 이미지 주소 검증. 배너 업로드 주소로 올린 파일만 배너로 쓸 수 있다.
 *
 * 이미지 주소는 요청 본문으로 들어오므로 그대로 믿지 않는다. 외부 사이트 이미지(추적·악성 이미지)나
 * 다른 용도로 올린 사진을 배너로 걸 수 없게, 업로드 주소 발급 때 만든 형태와 정확히 같을 때만 받는다.
 * 발급 형태: {publicBaseUrl}/banners/{UUID}.{jpg|png|webp} (ObjectStoragePresigner 참고)
 */
@Component
@RequiredArgsConstructor
public class BannerImageUrlPolicy {

    static final String KEY_PREFIX = "banners";

    // UUID + 허용 확장자만. 경로 조작(.., /, ?, #)이 끼어들 자리가 없다.
    private static final Pattern FILE_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final ObjectProvider<ObjectStorageProperties> storageProperties;

    public String requireBannerImage(String imageUrl) {
        ObjectStorageProperties storage = storageProperties.getIfAvailable();
        if (storage == null) {
            throw new BusinessException(UserErrorCode.STORAGE_NOT_CONFIGURED);
        }
        String base = storage.publicBaseUrl() + "/" + KEY_PREFIX + "/";
        if (imageUrl == null || !imageUrl.startsWith(base)
                || !FILE_NAME.matcher(imageUrl.substring(base.length())).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_BANNER_IMAGE);
        }
        return imageUrl;
    }
}
