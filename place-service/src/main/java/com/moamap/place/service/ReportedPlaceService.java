package com.moamap.place.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.ReportedPlaceResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.ReportReason;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.repository.PlaceReportRepository;
import com.moamap.place.repository.PlaceReportRepository.ReasonCountRow;
import com.moamap.place.repository.PlaceReportRepository.ReportedPlaceRow;
import com.moamap.place.repository.PlaceRepository;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방장·관리자가 자기 지도의 신고된 장소를 본다. 유지할지 삭제할지 판단하는 화면이며, 삭제는 기존 장소 삭제 API로 한다.
 *
 * 신고자는 보여주지 않는다(보복 방지). 신고 많은 순 → 최근 신고 순이고, 이미 삭제한 장소는 빠진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportedPlaceService {

    static final int MAX_PAGE_SIZE = 50;

    private final PlaceReportRepository placeReportRepository;
    private final PlaceRepository placeRepository;
    private final MapClient mapClient;

    public PageResponse<ReportedPlaceResponse> findReportedPlaces(Long mapId, Long userId, Pageable pageable) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        MapMemberRole role = mapClient.getMemberInfo(mapId, userId).role();
        if (role != MapMemberRole.OWNER && role != MapMemberRole.ADMIN) {
            throw new BusinessException(PlaceErrorCode.NOT_REVIEWER);
        }

        // 정렬은 쿼리가 정한다(신고 많은 순). 클라이언트가 보낸 sort는 무시한다.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        Page<ReportedPlaceRow> rows = placeReportRepository.findReportedPlaces(mapId, unsorted);
        List<Long> placeIds = rows.stream().map(ReportedPlaceRow::getPlaceId).toList();
        Map<Long, Place> places = placeRepository.findAllById(placeIds).stream()
            .collect(Collectors.toMap(Place::getId, Function.identity()));
        Map<Long, Map<ReportReason, Long>> reasons = reasonsByPlace(placeIds);

        return PageResponse.from(rows.map(row -> {
            Place place = places.get(row.getPlaceId());
            String photoUrl = place.getPhotoUrls().isEmpty() ? null : place.getPhotoUrls().get(0);
            return new ReportedPlaceResponse(place.getId(), place.getName(), photoUrl, row.getReportCount(),
                reasons.getOrDefault(place.getId(), Map.of()), row.getLastReportedAt());
        }));
    }

    private Map<Long, Map<ReportReason, Long>> reasonsByPlace(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<ReportReason, Long>> result = new HashMap<>();
        for (ReasonCountRow row : placeReportRepository.countReasons(placeIds)) {
            result.computeIfAbsent(row.getPlaceId(), id -> new EnumMap<>(ReportReason.class))
                .put(row.getReason(), row.getReportCount());
        }
        return result;
    }
}
