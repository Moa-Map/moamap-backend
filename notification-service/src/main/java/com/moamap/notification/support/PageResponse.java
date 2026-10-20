package com.moamap.notification.support;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지 응답 포맷. Spring의 PageImpl을 그대로 직렬화할 때의 불안정성을 피하기 위한 안정적 래퍼.
 *
 * map-service·user-service의 PageResponse와 모양을 맞춘다. 앱이 서비스마다 다른 페이지 형식을 다루지 않게 하기 위해서다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
