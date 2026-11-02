package com.moamap.map.service;

import com.moamap.map.dto.MapLeaveResponse;
import com.moamap.map.user.UserClient;
import com.moamap.map.user.dto.UserProfileResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지도 나가기 응답을 만든다.
 *
 * 나가기(MapService.leave)는 지도 행을 잠근 트랜잭션에서 끝내고, 새 방장의 닉네임은 그 뒤에 따로 가져온다.
 * user-service 호출 동안 지도를 잠가 두면 같은 지도의 다른 요청이 그만큼 기다리게 된다.
 * 그래서 이 클래스에는 트랜잭션을 걸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MapLeaveService {

    private final MapService mapService;
    private final UserClient userClient;

    public MapLeaveResponse leave(Long mapId, Long userId, Long newOwnerId) {
        return mapService.leave(mapId, userId, newOwnerId)
            .map(successorId -> MapLeaveResponse.handedOverTo(successorId, nicknameOf(successorId)))
            .orElseGet(MapLeaveResponse::notOwner);
    }

    /** 표시용이라 실패해도 나가기는 성공으로 둔다(UserClient는 실패하면 빈 결과를 준다). */
    private String nicknameOf(Long userId) {
        UserProfileResponse profile = userClient.findProfiles(List.of(userId)).get(userId);
        return profile == null ? null : profile.nickname();
    }
}
