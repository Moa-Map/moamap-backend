package com.moamap.map.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.dto.MapCreateRequest;
import com.moamap.map.dto.MapDetailResponse;
import com.moamap.map.dto.MapMemberRoleResponse;
import com.moamap.map.dto.MapMemberRoleUpdateRequest;
import com.moamap.map.dto.MapMemberRoleUpdateResponse;
import com.moamap.map.dto.MapSort;
import com.moamap.map.dto.MapSummaryResponse;
import com.moamap.map.dto.MapUpdateRequest;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapService {

    private static final int MAX_INVITE_CODE_TRIES = 5;

    private final MapEntityRepository mapRepository;
    private final MapMemberRepository mapMemberRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public MapDetailResponse create(MapCreateRequest request, Long ownerId) {
        MapType type = request.visibility().toMapType();
        String inviteCode = (type == MapType.PRIVATE) ? generateUniqueInviteCode() : null;

        MapEntity map = MapEntity.create(
            request.name(), request.description(), request.imageUrl(),
            type, ownerId, request.tags(), inviteCode
        );
        mapRepository.save(map);
        mapMemberRepository.save(MapMember.of(map.getId(), ownerId, MapRole.OWNER));

        return assembleDetail(map, ownerId, MapRole.OWNER);
    }

    public MapDetailResponse getDetail(Long mapId, Long requesterId) {
        MapEntity map = getMapOrThrow(mapId);
        MapRole role = roleOf(mapId, requesterId);
        if (map.isPrivate() && role == MapRole.NONE) {
            throw new BusinessException(MapErrorCode.NOT_MAP_MEMBER, "프라이빗 지도는 멤버만 조회할 수 있습니다.");
        }
        return assembleDetail(map, requesterId, role);
    }

    public Page<MapSummaryResponse> getCommunityMaps(String tag, MapSort sort, Pageable pageable, Long requesterId) {
        Pageable sorted = withSort(pageable, sort);
        Page<MapEntity> maps = (tag == null || tag.isBlank())
            ? mapRepository.findByType(MapType.COMMUNITY, sorted)
            : mapRepository.findByTypeAndTag(MapType.COMMUNITY, tag, sorted);
        return toSummaryPage(maps, requesterId);
    }

    public Page<MapSummaryResponse> getOfficialMaps(Pageable pageable, Long requesterId) {
        Pageable sorted = withSort(pageable, MapSort.LATEST);
        Page<MapEntity> maps = mapRepository.findByType(MapType.OFFICIAL, sorted);
        return toSummaryPage(maps, requesterId);
    }

    public Page<MapSummaryResponse> getMyMaps(MapType type, Pageable pageable, Long requesterId) {
        Pageable sorted = withSort(pageable, MapSort.LATEST);
        Page<MapEntity> maps = mapRepository.findJoinedByType(requesterId, type, sorted);
        // 내가 참여한 목록이므로 joined은 항상 true.
        return maps.map(map -> MapSummaryResponse.of(map, true));
    }

    @Transactional
    public MapDetailResponse update(Long mapId, Long requesterId, MapUpdateRequest request) {
        MapEntity map = getMapOrThrow(mapId);
        MapRole role = roleOf(mapId, requesterId);
        requireManagePermission(role);
        map.update(request.name(), request.description(), request.imageUrl(), request.tags());
        return assembleDetail(map, requesterId, role);
    }

    @Transactional
    public void delete(Long mapId, Long requesterId) {
        MapEntity map = getMapOrThrow(mapId);
        if (!map.isOwnedBy(requesterId)) {
            throw new BusinessException(MapErrorCode.NOT_MAP_OWNER);
        }
        if (map.isPersonal()) {
            throw new BusinessException(MapErrorCode.CANNOT_DELETE_PERSONAL_MAP);
        }
        mapMemberRepository.deleteByMapId(mapId);
        mapRepository.delete(map);
    }

    /**
     * 가입한 사용자에게 나만의 지도를 하나 만들어준다. 이미 있으면 아무것도 하지 않는다.
     *
     * 메시지는 최소 한 번 배달되므로 같은 이벤트가 다시 올 수 있다. 사전 조회로 흔한 중복을 싸게 걸러내되,
     * 동시에 처리되는 경우까지는 막지 못하므로 DB 유니크 제약을 최종 방어선으로 둔다.
     */
    @Transactional
    public void createPersonalMapIfAbsent(Long userId, String mapName) {
        if (mapRepository.existsByOwnerIdAndPersonalIsTrue(userId)) {
            return;
        }
        try {
            MapEntity map = mapRepository.saveAndFlush(MapEntity.createPersonal(userId, mapName));
            mapMemberRepository.save(MapMember.of(map.getId(), userId, MapRole.OWNER));
        } catch (DataIntegrityViolationException e) {
            // 동시 처리로 이미 만들어졌다. 목표 상태(지도가 하나 존재)는 달성됐으므로 정상 종료한다.
            log.info("나만의 지도가 이미 생성되어 있어 건너뛴다. userId={}", userId);
        }
    }

    @Transactional
    public MapDetailResponse joinCommunity(Long mapId, Long userId) {
        MapEntity map = getMapOrThrow(mapId);
        if (map.getType() == MapType.PRIVATE) {
            throw new BusinessException(MapErrorCode.NOT_COMMUNITY_MAP);
        }
        addMember(map, userId);
        return assembleDetail(map, userId, MapRole.MEMBER);
    }

    @Transactional
    public MapDetailResponse joinByInviteCode(String inviteCode, Long userId) {
        MapEntity map = mapRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new BusinessException(MapErrorCode.INVALID_INVITE_CODE));
        addMember(map, userId);
        return assembleDetail(map, userId, MapRole.MEMBER);
    }

    @Transactional
    public void leave(Long mapId, Long userId) {
        MapEntity map = getMapOrThrow(mapId);
        MapMember member = mapMemberRepository.findByMapIdAndUserId(mapId, userId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.NOT_MAP_MEMBER));
        if (member.getRole() == MapRole.OWNER) {
            throw new BusinessException(MapErrorCode.OWNER_CANNOT_LEAVE);
        }
        mapMemberRepository.delete(member);
        map.decreaseMemberCount();
    }

    /**
     * 멤버 역할 조회. 미참여 시 role=NONE으로 응답한다. (place-service 승인 판단용)
     */
    public MapMemberRoleResponse getMemberRole(Long mapId, Long userId) {
        MapEntity map = getMapOrThrow(mapId);
        return MapMemberRoleResponse.of(map.getType(), roleOf(mapId, userId));
    }

    @Transactional
    public MapMemberRoleUpdateResponse changeMemberRole(
        Long mapId, Long requesterId, Long targetUserId, MapMemberRoleUpdateRequest request
    ) {
        MapRole requestedRole = request.role();
        if (requestedRole != MapRole.ADMIN && requestedRole != MapRole.MEMBER) {
            throw new BusinessException(MapErrorCode.INVALID_ROLE_ASSIGNMENT);
        }

        MapEntity map = getMapOrThrow(mapId);
        if (!map.isOwnedBy(requesterId)) {
            throw new BusinessException(MapErrorCode.NOT_MAP_OWNER);
        }

        MapMember target = mapMemberRepository.findByMapIdAndUserId(mapId, targetUserId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.TARGET_NOT_MAP_MEMBER));
        if (target.getRole() == MapRole.OWNER) {
            throw new BusinessException(MapErrorCode.CANNOT_CHANGE_OWNER_ROLE);
        }

        if (target.getRole() != requestedRole) {
            target.changeRole(requestedRole);
        }
        return MapMemberRoleUpdateResponse.of(mapId, targetUserId, requestedRole);
    }

    private void addMember(MapEntity map, Long userId) {
        if (mapMemberRepository.existsByMapIdAndUserId(map.getId(), userId)) {
            throw new BusinessException(MapErrorCode.ALREADY_JOINED);
        }
        mapMemberRepository.save(MapMember.of(map.getId(), userId, MapRole.MEMBER));
        map.increaseMemberCount();
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < MAX_INVITE_CODE_TRIES; i++) {
            String code = inviteCodeGenerator.generate();
            if (!mapRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new BusinessException(MapErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    private MapDetailResponse assembleDetail(MapEntity map, Long requesterId, MapRole role) {
        boolean canSeeInviteCode = (role == MapRole.OWNER || role == MapRole.ADMIN);
        return MapDetailResponse.of(map, role, canSeeInviteCode ? map.getInviteCode() : null);
    }

    private Page<MapSummaryResponse> toSummaryPage(Page<MapEntity> maps, Long requesterId) {
        Set<Long> joinedIds = joinedMapIds(requesterId, maps.getContent());
        return maps.map(map -> MapSummaryResponse.of(map, joinedIds.contains(map.getId())));
    }

    private Set<Long> joinedMapIds(Long userId, List<MapEntity> maps) {
        if (userId == null || maps.isEmpty()) {
            return Set.of();
        }
        List<Long> mapIds = maps.stream().map(MapEntity::getId).toList();
        return mapMemberRepository.findByUserIdAndMapIdIn(userId, mapIds).stream()
            .map(MapMember::getMapId)
            .collect(Collectors.toSet());
    }

    private MapRole roleOf(Long mapId, Long userId) {
        if (userId == null) {
            return MapRole.NONE;
        }
        return mapMemberRepository.findByMapIdAndUserId(mapId, userId)
            .map(MapMember::getRole)
            .orElse(MapRole.NONE);
    }

    private void requireManagePermission(MapRole role) {
        if (role != MapRole.OWNER && role != MapRole.ADMIN) {
            throw new BusinessException(MapErrorCode.NO_MANAGE_PERMISSION);
        }
    }

    private MapEntity getMapOrThrow(Long mapId) {
        return mapRepository.findById(mapId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.MAP_NOT_FOUND));
    }

    private Pageable withSort(Pageable pageable, MapSort sort) {
        Sort resolved = switch (sort == null ? MapSort.POPULAR : sort) {
            case POPULAR -> Sort.by(Sort.Direction.DESC, "memberCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
    }
}
