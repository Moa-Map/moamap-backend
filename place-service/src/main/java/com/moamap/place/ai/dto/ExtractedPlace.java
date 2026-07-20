package com.moamap.place.ai.dto;

/**
 * Gemini가 인스타그램 설명글에서 추출한 장소 단서.
 * 카카오맵 키워드 검색 정확도를 높이기 위해 장소명과 지역 힌트를 분리해 받는다.
 */
public record ExtractedPlace(
    String name,
    String region
) {
    public String toSearchKeyword() {
        String trimmedName = name == null ? "" : name.trim();
        if (region != null && !region.isBlank()) {
            return (region.trim() + " " + trimmedName).trim();
        }
        return trimmedName;
    }
}
