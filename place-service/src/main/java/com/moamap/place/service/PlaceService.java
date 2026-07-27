package com.moamap.place.service;

import java.util.ArrayList;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.dto.PlaceResponse;
import com.moamap.place.dto.PlaceUpdateRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private static final String DUPLICATE_PLACE_CONSTRAINT = "uk_places_map_kakao_place";

    private final PlaceRepository placeRepository;
    private final MapClient mapClient;

    @Transactional
    public PlaceResponse create(PlaceCreateRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        MapMemberResponse memberInfo = mapClient.getMemberInfo(request.mapId(), userId);
        PlaceStatus status = resolveInitialStatus(memberInfo);
        checkDuplicate(request.mapId(), request.kakaoPlaceId());

        Place place = Place.builder()
            .name(request.name())
            .address(request.address())
            .roadAddress(request.roadAddress())
            .lat(request.lat())
            .lng(request.lng())
            .category(request.category())
            .kakaoPlaceId(request.kakaoPlaceId())
            .sourceType(request.sourceType())
            .sourceUrl(request.sourceUrl())
            .description(request.description())
            .tags(request.tags() == null ? new ArrayList<>() : request.tags())
            .mapId(request.mapId())
            .createdBy(userId)
            .status(status)
            .build();
        // checkDuplicate는 흔한 경우를 빠르게 막기 위한 사전 체크일 뿐이고,
        // 동시 요청 race condition은 uk_places_map_kakao_place 유니크 제약으로 막는다.
        try {
            return PlaceResponse.from(placeRepository.saveAndFlush(place));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicatePlaceConstraintViolation(e)) {
                throw new BusinessException(PlaceErrorCode.DUPLICATE_PLACE);
            }
            throw e;
        }
    }

    // 길이 초과, NOT NULL 등 다른 무결성 위반까지 DUPLICATE_PLACE로 뭉개지 않도록,
    // uk_places_map_kakao_place 제약 위반일 때만 변환한다.
    private boolean isDuplicatePlaceConstraintViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null && constraintName.toLowerCase().contains(DUPLICATE_PLACE_CONSTRAINT);
        }
        return false;
    }

    private PlaceStatus resolveInitialStatus(MapMemberResponse memberInfo) {
        if (memberInfo.role() == MapMemberRole.NONE) {
            throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
        }
        if (memberInfo.mapType() == MapType.OFFICIAL) {
            throw new BusinessException(PlaceErrorCode.OFFICIAL_MAP_NOT_REGISTRABLE);
        }
        if (memberInfo.mapType() == MapType.PRIVATE) {
            return PlaceStatus.APPROVED;
        }
        // COMMUNITY: 방장/관리자는 바로 승인, 일반 멤버는 승인 대기.
        return switch (memberInfo.role()) {
            case OWNER, ADMIN -> PlaceStatus.APPROVED;
            case MEMBER -> PlaceStatus.PENDING;
            case NONE -> throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
        };
    }

    private void checkDuplicate(Long mapId, String kakaoPlaceId) {
        if (placeRepository.existsByMapIdAndKakaoPlaceIdAndDeletedAtIsNull(mapId, kakaoPlaceId)) {
            throw new BusinessException(PlaceErrorCode.DUPLICATE_PLACE);
        }
    }

    public PlaceResponse findById(Long id) {
        return PlaceResponse.from(getOrThrow(id));
    }

    public PageResponse<PlaceResponse> findAllByMapId(Long mapId, Pageable pageable) {
        return PageResponse.from(placeRepository.findByMapIdAndStatusAndDeletedAtIsNull(mapId, PlaceStatus.APPROVED, pageable)
            .map(PlaceResponse::from));
    }

    public PageResponse<PlaceResponse> findPendingByMapId(Long mapId, Long userId, Pageable pageable) {
        requireReviewer(mapId, userId);
        return PageResponse.from(placeRepository.findByMapIdAndStatusAndDeletedAtIsNull(mapId, PlaceStatus.PENDING, pageable)
            .map(PlaceResponse::from));
    }

    @Transactional
    public PlaceResponse update(Long id, Long userId, PlaceUpdateRequest request) {
        Place place = getOrThrow(id);
        checkModifyPermission(place, userId);
        place.update(request.name(), request.address(), request.roadAddress(), request.lat(), request.lng(),
            request.category(), request.description(), request.tags());
        return PlaceResponse.from(place);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Place place = getOrThrow(id);
        checkModifyPermission(place, userId);
        place.delete(userId);
    }

    @Transactional
    public PlaceResponse approve(Long id, Long userId) {
        Place place = getOrThrow(id);
        requireReviewer(place.getMapId(), userId);
        checkPending(place);
        place.approve(userId);
        return PlaceResponse.from(place);
    }

    @Transactional
    public PlaceResponse reject(Long id, Long userId) {
        Place place = getOrThrow(id);
        requireReviewer(place.getMapId(), userId);
        checkPending(place);
        place.reject(userId);
        return PlaceResponse.from(place);
    }

    private Place getOrThrow(Long id) {
        return placeRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    /**
     * 장소 수정/삭제 권한.
     * - PRIVATE 지도: 멤버면 누구든 가능
     * - COMMUNITY 지도: 방장/관리자는 누구 장소든 가능, 그 외 멤버는 본인이 등록한 장소만 가능
     * - 그 외(OFFICIAL 등): 본인이 등록한 장소만 가능
     */
    private void checkModifyPermission(Place place, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        MapMemberResponse memberInfo = mapClient.getMemberInfo(place.getMapId(), userId);
        if (memberInfo.role() == MapMemberRole.NONE) {
            throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
        }
        if (memberInfo.mapType() == MapType.PRIVATE || isReviewer(memberInfo.role())) {
            return;
        }
        if (!place.getCreatedBy().equals(userId)) {
            throw new BusinessException(PlaceErrorCode.NOT_PLACE_OWNER);
        }
    }

    private void requireReviewer(Long mapId, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        MapMemberResponse memberInfo = mapClient.getMemberInfo(mapId, userId);
        if (!isReviewer(memberInfo.role())) {
            throw new BusinessException(PlaceErrorCode.NOT_REVIEWER);
        }
    }

    private void checkPending(Place place) {
        if (place.getStatus() != PlaceStatus.PENDING) {
            throw new BusinessException(PlaceErrorCode.ALREADY_PROCESSED);
        }
    }

    private boolean isReviewer(MapMemberRole role) {
        return role == MapMemberRole.OWNER || role == MapMemberRole.ADMIN;
    }
}
