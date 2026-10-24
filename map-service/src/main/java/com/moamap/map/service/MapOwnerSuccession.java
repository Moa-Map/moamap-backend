package com.moamap.map.service;

import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.repository.MapMemberRepository;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 방장이 지도를 떠날 때(나가기·회원 탈퇴) 다음 방장을 정하고 넘긴다. 두 경로가 같은 규칙을 쓰도록 한 곳에 둔다.
 *
 * 방장 없는 지도가 남으면 남은 멤버가 지도를 고치거나 지울 수 없게 된다. 그래서 떠나는 방장은 반드시 넘기고 나간다.
 * 넘길 사람이 없을 때 지도를 지울지 막을지는 부르는 쪽이 정한다(탈퇴는 지우고, 나가기는 막는다).
 *
 * 호출하는 트랜잭션에서 지도 행을 잠가 둬야 한다. 후계자가 동시에 나가면 지도에 없는 사람이 방장이 될 수 있다.
 */
@Component
@RequiredArgsConstructor
public class MapOwnerSuccession {

    /** 방장을 넘겨받을 순서. 관리자가 먼저, 같은 역할이면 먼저 참여한 사람. 참여 시각이 같으면 ID 순으로 정한다. */
    static final Comparator<MapMember> SUCCESSION_ORDER = Comparator
        .comparing((MapMember member) -> member.getRole() == MapRole.ADMIN ? 0 : 1)
        .thenComparing(MapMember::getCreatedAt)
        .thenComparing(MapMember::getId);

    private final MapMemberRepository mapMemberRepository;

    /**
     * 떠나는 방장의 자리를 다음 사람에게 넘기고 방장의 참여 기록을 지운다.
     *
     * @return 새 방장의 참여 기록. 넘길 사람이 없으면 비어 있고, 이때는 아무것도 바꾸지 않는다
     */
    public Optional<MapMember> handOver(MapEntity map, MapMember owner) {
        Optional<MapMember> successor = nextOwner(map, owner);
        successor.ifPresent(next -> {
            next.changeRole(MapRole.OWNER);
            map.transferOwnership(next.getUserId());
            mapMemberRepository.delete(owner);
            map.decreaseMemberCount();
        });
        return successor;
    }

    private Optional<MapMember> nextOwner(MapEntity map, MapMember owner) {
        return mapMemberRepository.findByMapId(map.getId()).stream()
            .filter(member -> !member.getUserId().equals(owner.getUserId()))
            .min(SUCCESSION_ORDER);
    }
}
