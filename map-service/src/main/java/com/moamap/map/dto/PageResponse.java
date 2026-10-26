package com.moamap.map.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지 응답 포맷. Spring의 PageImpl을 그대로 직렬화할 때의 불안정성을 피하기 위한 안정적 래퍼.
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
