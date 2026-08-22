package com.moamap.map.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 로그 탭 접근 권한 검사. 게시물·댓글·사진 발급이 같은 규칙을 쓰기 때문에 한곳에 모은다.
 * 규칙의 근거는 이슈 #95의 권한·상태 분기 표다.
 */
@Component
@RequiredArgsConstructor
public class MapPostAccessPolicy {

    private final MapEntityRepository mapRepository;
    private final MapMemberRepository mapMemberRepository;

    /** 공식 지도는 멤버십이 없어 로그 탭 자체가 없다. 존재하지 않는 리소스로 다룬다. */
    public MapEntity requireLogTab(Long mapId) {
        MapEntity map = mapRepository.findById(mapId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.MAP_NOT_FOUND));
        if (map.getType() == MapType.OFFICIAL) {
            throw new BusinessException(MapErrorCode.MAP_POST_NOT_SUPPORTED);
        }
        return map;
    }

    /** 쓰기(작성·수정·삭제·사진 발급)는 지도 타입과 무관하게 멤버만 가능하다. */
    public MapRole requireWritable(Long mapId, Long userId) {
        requireLogin(userId);
        requireLogTab(mapId);
        return requireMember(mapId, userId);
    }

    /**
     * 커뮤니티 지도의 로그는 비멤버·비로그인도 읽을 수 있다(유입 목적). 프라이빗은 멤버만이다.
     */
    public void requireReadable(Long mapId, Long userId) {
        MapEntity map = requireLogTab(mapId);
        if (map.getType() == MapType.COMMUNITY) {
            return;
        }
        requireLogin(userId);
        requireMember(mapId, userId);
    }

    private MapRole requireMember(Long mapId, Long userId) {
        return mapMemberRepository.findByMapIdAndUserId(mapId, userId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.NOT_MAP_MEMBER))
            .getRole();
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    /** 작성자 본인이거나 지도를 관리하는 역할이면 지울 수 있다(부적절한 콘텐츠 정리 수단). */
    public boolean canDelete(boolean writtenByRequester, MapRole role) {
        return writtenByRequester || role == MapRole.OWNER || role == MapRole.ADMIN;
    }
}
