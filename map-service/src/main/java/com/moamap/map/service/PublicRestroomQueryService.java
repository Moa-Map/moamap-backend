package com.moamap.map.service;

import java.util.List;

import com.moamap.common.exception.BusinessException;
import com.moamap.map.dto.PublicRestroomDetailResponse;
import com.moamap.map.dto.PublicRestroomListResponse;
import com.moamap.map.dto.PublicRestroomMarkerResponse;
import com.moamap.map.dto.RestroomBoundsRequest;
import com.moamap.map.entity.PublicRestroom;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.PublicRestroomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicRestroomQueryService {

    // 지도 뷰포트에 마커가 과도하게 많으면 클라이언트 렌더링이 느려지므로 500건으로 제한한다.
    // 잘린 결과는 id 순이라 지역이 편중된다 — truncated=true면 프론트가 확대를 안내하는 것으로 합의(bbox 상한은 두지 않음).
    private static final int MARKER_LIMIT = 500;

    private final PublicRestroomRepository restroomRepository;

    public PublicRestroomListResponse listMarkers(RestroomBoundsRequest request) {
        List<PublicRestroom> rows = restroomRepository.findMarkersInBounds(
            request.swLat(), request.swLng(), request.neLat(), request.neLng(),
            PageRequest.of(0, MARKER_LIMIT + 1));

        boolean truncated = rows.size() > MARKER_LIMIT;
        List<PublicRestroomMarkerResponse> markers = rows.stream()
            .limit(MARKER_LIMIT)
            .map(PublicRestroomMarkerResponse::from)
            .toList();

        return new PublicRestroomListResponse(markers, truncated);
    }

    public PublicRestroomDetailResponse getDetail(Long id) {
        PublicRestroom restroom = restroomRepository.findByIdAndDeletedAtIsNullAndHiddenFalse(id)
            .orElseThrow(() -> new BusinessException(MapErrorCode.RESTROOM_NOT_FOUND));
        return PublicRestroomDetailResponse.from(restroom);
    }
}
