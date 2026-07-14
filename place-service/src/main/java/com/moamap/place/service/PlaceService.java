package com.moamap.place.service;

import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PlaceCreateRequest;
import com.moamap.place.dto.PlaceResponse;
import com.moamap.place.dto.PlaceUpdateRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final MapClient mapClient;

    @Transactional
    public PlaceResponse create(PlaceCreateRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        MapMemberResponse memberInfo = mapClient.getMemberInfo(request.mapId(), userId);
        PlaceStatus status = resolveInitialStatus(memberInfo);

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
            .mapId(request.mapId())
            .createdBy(userId)
            .status(status)
            .build();
        return PlaceResponse.from(placeRepository.save(place));
    }

    private PlaceStatus resolveInitialStatus(MapMemberResponse memberInfo) {
        if (memberInfo.role() == MapMemberRole.NONE) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "해당 지도의 멤버가 아닙니다.");
        }
        if (memberInfo.mapType() == MapType.OFFICIAL) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "공식 지도에는 장소를 추가할 수 없습니다.");
        }
        if (memberInfo.mapType() == MapType.PRIVATE) {
            return PlaceStatus.APPROVED;
        }
        // COMMUNITY: 방장/관리자는 바로 승인, 일반 멤버는 승인 대기.
        return switch (memberInfo.role()) {
            case OWNER, ADMIN -> PlaceStatus.APPROVED;
            case MEMBER -> PlaceStatus.PENDING;
            case NONE -> throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "해당 지도의 멤버가 아닙니다.");
        };
    }

    public PlaceResponse findById(Long id) {
        return PlaceResponse.from(getOrThrow(id));
    }

    public List<PlaceResponse> findAllByMapId(Long mapId) {
        return placeRepository.findByMapIdAndStatusAndDeletedAtIsNull(mapId, PlaceStatus.APPROVED).stream()
            .map(PlaceResponse::from)
            .toList();
    }

    public List<PlaceResponse> findPendingByMapId(Long mapId, Long userId) {
        requireReviewer(mapId, userId);
        return placeRepository.findByMapIdAndStatusAndDeletedAtIsNull(mapId, PlaceStatus.PENDING).stream()
            .map(PlaceResponse::from)
            .toList();
    }

    @Transactional
    public PlaceResponse update(Long id, Long userId, PlaceUpdateRequest request) {
        Place place = getOrThrow(id);
        checkOwner(place, userId);
        place.update(request.name(), request.address(), request.roadAddress(), request.lat(), request.lng(),
            request.category(), request.description());
        return PlaceResponse.from(place);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Place place = getOrThrow(id);
        checkOwner(place, userId);
        place.delete();
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
            .orElseThrow(() -> new BusinessException(CommonErrorCode.ENTITY_NOT_FOUND, "장소를 찾을 수 없습니다."));
    }

    private void checkOwner(Place place, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!place.getCreatedBy().equals(userId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "해당 장소에 대한 권한이 없습니다.");
        }
    }

    private void requireReviewer(Long mapId, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        MapMemberResponse memberInfo = mapClient.getMemberInfo(mapId, userId);
        if (!isReviewer(memberInfo.role())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "방장/관리자만 접근할 수 있습니다.");
        }
    }

    private void checkPending(Place place) {
        if (place.getStatus() != PlaceStatus.PENDING) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "이미 처리된 장소입니다.");
        }
    }

    private boolean isReviewer(MapMemberRole role) {
        return role == MapMemberRole.OWNER || role == MapMemberRole.ADMIN;
    }
}
