package com.moamap.place.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceActivityResponse;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceActivityRepository;
import com.moamap.place.user.UserClient;
import com.moamap.place.user.dto.UserProfileResponse;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceActivityService {

    // 표시 자리를 절대 비워두지 않기 위한 폴백. actorId 없음/탈퇴자/조회 실패 모두 이 값으로 통일한다.
    private static final String UNKNOWN_ACTOR = "알 수 없음";

    private final PlaceActivityRepository placeActivityRepository;
    private final MapClient mapClient;
    private final UserClient userClient;

    // 클래스 레벨 @Transactional을 두지 않는다 - MapClient/UserClient 호출 동안 DB 커넥션을 잡지 않기 위해서다.
    public PageResponse<PlaceActivityResponse> findByMapId(Long mapId, Long userId, Pageable pageable) {
        MapMemberResponse memberInfo = mapClient.getMemberInfo(mapId, userId);
        // mapType은 map-service 응답을 손으로 베낀 복사본이라 필드명이 어긋나면 null이 들어올 수 있다.
        // "COMMUNITY가 아니면 검증"처럼 부정 조건으로 짜지 않고 유형을 하나씩 매칭시켜, 어디에도
        // 걸리지 않으면(null 포함) 차단하는 fail-closed 구조로 둔다.
        if (memberInfo.mapType() == MapType.OFFICIAL) {
            throw new BusinessException(PlaceErrorCode.OFFICIAL_MAP_NO_ACTIVITY_LOG);
        } else if (memberInfo.mapType() == MapType.COMMUNITY) {
            // 로그인한 사용자면 통과 - 멤버십 검증 없음.
        } else if (memberInfo.mapType() == MapType.PRIVATE) {
            if (memberInfo.role() != MapMemberRole.OWNER
                && memberInfo.role() != MapMemberRole.ADMIN
                && memberInfo.role() != MapMemberRole.MEMBER) {
                throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
            }
        } else {
            throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
        }
        boolean includeReviews = memberInfo.mapType() == MapType.PRIVATE;
        Page<PlaceActivityResponse> page = placeActivityRepository.findActivities(mapId, includeReviews, pageable)
            .map(PlaceActivityResponse::from);
        return PageResponse.from(enrichWithActorProfiles(page));
    }

    // UserClient는 표시용 부가정보라 실패해도 조회 전체를 죽이면 안 된다. 서로 다른 행위자 수만큼만
    // 모아 페이지당 최대 1회만 호출해 N+1을 피한다.
    private Page<PlaceActivityResponse> enrichWithActorProfiles(Page<PlaceActivityResponse> page) {
        Set<Long> actorIds = new LinkedHashSet<>();
        for (PlaceActivityResponse row : page.getContent()) {
            if (row.actorId() != null) {
                actorIds.add(row.actorId());
            }
        }
        if (actorIds.isEmpty()) {
            return page.map(row -> row.withActorProfile(UNKNOWN_ACTOR, null));
        }
        Map<Long, UserProfileResponse> profiles;
        try {
            profiles = userClient.findProfiles(actorIds);
        } catch (RuntimeException e) {
            // UserClient는 원칙적으로 예외를 던지지 않지만 방어적으로 한 번 더 막는다.
            log.warn("user-service 프로필 조회 중 예외가 발생해 닉네임 없이 진행합니다.", e);
            profiles = Map.of();
        }
        Map<Long, UserProfileResponse> resolvedProfiles = profiles == null ? Map.of() : profiles;
        return page.map(row -> {
            UserProfileResponse profile = row.actorId() == null ? null : resolvedProfiles.get(row.actorId());
            return row.withActorProfile(
                profile == null ? UNKNOWN_ACTOR : profile.nickname(),
                profile == null ? null : profile.profileImageUrl()
            );
        });
    }
}
