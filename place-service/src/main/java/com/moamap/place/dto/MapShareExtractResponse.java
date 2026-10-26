package com.moamap.place.dto;

import java.util.List;
import com.moamap.place.entity.PlaceSourceType;

/**
 * 지도 공유 링크 추출 결과.
 *
 * declaredCount는 서비스가 알려주는 "리스트에 있어야 할 수", extractedCount는 절단 후
 * 실제로 재매칭까지 처리한 수다(= matched.size() + unmatched.size()).
 * truncated는 파싱된 장소가 상한을 넘어 잘렸는지를 뜻한다.
 */
public record MapShareExtractResponse(
    PlaceSourceType source,
    String sourceUrl,
    String listName,
    String owner,
    Integer declaredCount,
    int extractedCount,
    boolean truncated,
    List<MapSharePlaceCandidate> matched,
    List<UnmatchedPlaceResponse> unmatched
) {
}
