package com.moamap.map.service;

import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.event.ActivitySignal;
import com.moamap.map.repository.MapMemberRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 방장이 지도를 떠날 때(나가기·회원 탈퇴) 방장을 넘긴다. 두 경로가 같은 규칙을 쓰도록 한 곳에 둔다.
 *
 * 방장 없는 지도가 남으면 남은 멤버가 지도를 고치거나 지울 수 없게 된다. 그래서 떠나는 방장은 반드시 넘기고 나간다.
 * 나가기는 방장이 후보 중에서 직접 고르고, 회원 탈퇴는 고를 사람이 없으므로 후보 중 첫 사람에게 자동으로 넘긴다.
 * 후보가 없을 때 지도를 지울지 막을지는 부르는 쪽이 정한다(탈퇴는 지우고, 나가기는 막는다).
 *
 * 호출하는 트랜잭션에서 지도 행을 잠가 둬야 한다. 후보가 동시에 나가면 지도에 없는 사람이 방장이 될 수 있다.
 *
 * 넘기면 새 방장에게 알림을 보낸다(map.owner.transferred). 나가기·탈퇴 어느 경로로 넘어가도 같은 알림이 가도록 여기서 발행한다.
 */
@Component
@RequiredArgsConstructor
public class MapOwnerSuccession {

    /** 후보를 보여주고 자동으로 고르는 순서. 먼저 참여한 사람이 앞이고, 참여 시각이 같으면 ID 순으로 정한다. */
    static final Comparator<MapMember> CANDIDATE_ORDER = Comparator
        .comparing(MapMember::getCreatedAt)
        .thenComparing(MapMember::getId);

    private final MapMemberRepository mapMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 방장을 넘겨받을 수 있는 사람. 관리자가 있으면 관리자만, 없으면 일반 멤버 전체다.
     * 관리자는 방장이 지도 운영을 맡긴 사람이라, 관리자를 두고 일반 멤버에게 넘기지 않는다.
     */
    public List<MapMember> candidates(MapEntity map) {
        List<MapMember> others = mapMemberRepository.findByMapId(map.getId()).stream()
            .filter(member -> !map.isOwnedBy(member.getUserId()))
            .sorted(CANDIDATE_ORDER)
            .toList();
        List<MapMember> admins = others.stream()
            .filter(member -> member.getRole() == MapRole.ADMIN)
            .toList();
        return admins.isEmpty() ? others : admins;
    }

    /**
     * 회원 탈퇴용. 후보 중 첫 사람에게 넘긴다.
     *
     * @return 새 방장의 참여 기록. 넘길 사람이 없으면 비어 있고, 이때는 아무것도 바꾸지 않는다
     */
    public Optional<MapMember> handOverToFirstCandidate(MapEntity map, MapMember owner) {
        Optional<MapMember> successor = candidates(map).stream().findFirst();
        successor.ifPresent(next -> handOver(map, owner, next));
        return successor;
    }

    /** 떠나는 방장의 자리를 successor에게 넘기고 방장의 참여 기록을 지운다. successor가 후보인지는 부르는 쪽이 확인한다. */
    public void handOver(MapEntity map, MapMember owner, MapMember successor) {
        successor.changeRole(MapRole.OWNER);
        map.transferOwnership(successor.getUserId());
        mapMemberRepository.delete(owner);
        map.decreaseMemberCount();
        // 행동한 사람(이전 방장)은 알림에 담지 않는다. 탈퇴한 회원일 수 있고, 알림 문구도 이전 방장을 드러내지 않는다.
        eventPublisher.publishEvent(new ActivitySignal(
            ActivitySignal.MAP_OWNER_TRANSFERRED, successor.getUserId(), null, map.getId(), map.getId()));
    }
}
