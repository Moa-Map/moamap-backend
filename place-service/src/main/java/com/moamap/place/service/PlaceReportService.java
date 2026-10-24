package com.moamap.place.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.ReportRequest;
import com.moamap.place.dto.ReportResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceReport;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.event.ReportFiled;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceReportRepository;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 신고. 운영자와 그 지도의 방장·관리자가 받는다.
 *
 * 그 장소를 볼 수 있는 사람이면 신고할 수 있다(MapViewPolicy). 지도에 올라온(승인된) 장소만 대상이다.
 * 신고가 쌓여도 자동으로 숨기지 않는다. 방장·관리자가 유지·삭제를 판단하고, 운영자가 함께 본다.
 * 같은 장소에 신고가 계속 들어와도 방장·관리자 알림은 첫 신고 때 한 번만 보낸다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlaceReportService {

    private final PlaceRepository placeRepository;
    private final PlaceReportRepository placeReportRepository;
    private final MapClient mapClient;
    private final MapViewPolicy mapViewPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public ReportResponse report(Long placeId, Long userId, ReportRequest request) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Place place = placeRepository.findByIdAndDeletedAtIsNull(placeId)
            .filter(found -> found.getStatus() == PlaceStatus.APPROVED)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
        MapMemberResponse memberInfo = mapClient.getMemberInfo(place.getMapId(), userId);
        mapViewPolicy.requireViewable(memberInfo);
        if (place.getCreatedBy().equals(userId)) {
            throw new BusinessException(PlaceErrorCode.CANNOT_REPORT_OWN_PLACE);
        }
        // 흔한 중복은 미리 걸러내고, 동시에 들어온 요청은 유일 제약이 막는다.
        if (placeReportRepository.existsByPlaceIdAndReporterId(placeId, userId)) {
            throw new BusinessException(PlaceErrorCode.ALREADY_REPORTED_PLACE);
        }
        boolean firstReport = !placeReportRepository.existsByPlaceId(placeId);

        PlaceReport report;
        try {
            report = placeReportRepository.saveAndFlush(
                PlaceReport.of(placeId, place.getMapId(), userId, request.reason(), request.detail()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(PlaceErrorCode.ALREADY_REPORTED_PLACE);
        }
        // 공식 지도는 방장·관리자가 없어 운영자만 받는다.
        boolean notifyManagers = firstReport && memberInfo.mapType() != MapType.OFFICIAL;
        eventPublisher.publishEvent(notifyManagers
            ? ReportFiled.toOperatorsAndManagers(report.getId(), place.getMapId(), userId, placeId)
            : ReportFiled.toOperators(report.getId(), place.getMapId(), userId));
        return ReportResponse.from(report);
    }
}
