package com.moamap.map.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 map-service 데이터를 정리한다.
 *
 * - 참여 중인 지도에서 빠진다(멤버 수 갱신).
 * - 방장이던 지도는 다음 사람에게 넘긴다. 넘길 사람이 없거나 나만의 지도면 지도를 지운다.
 *   방장 없는 지도가 남으면 남은 멤버가 지도를 고치거나 지울 수 없게 된다.
 * - 쓴 게시글과 댓글을 지운다(작성자가 직접 지울 때와 같은 소프트 삭제).
 *
 * 같은 이벤트가 다시 와도 결과가 같다. 참여 기록은 이미 없고, 이미 지운 글은 다시 건드리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapWithdrawalCleanupService {

    /** 방장을 넘겨받을 순서. 관리자가 먼저, 같은 역할이면 먼저 참여한 사람. 참여 시각이 같으면 ID 순으로 정한다. */
    private static final Comparator<MapMember> SUCCESSION_ORDER = Comparator
        .comparing((MapMember member) -> member.getRole() == MapRole.ADMIN ? 0 : 1)
        .thenComparing(MapMember::getCreatedAt)
        .thenComparing(MapMember::getId);

    private final MapEntityRepository mapRepository;
    private final MapMemberRepository mapMemberRepository;
    private final MapPostRepository mapPostRepository;
    private final MapPostCommentRepository mapPostCommentRepository;

    @Transactional
    public void cleanUp(Long userId) {
        for (MapMember membership : mapMemberRepository.findByUserId(userId)) {
            leave(membership);
        }
        LocalDateTime now = LocalDateTime.now();
        mapPostRepository.softDeleteAllByUserId(userId, now);
        mapPostCommentRepository.softDeleteAllByUserId(userId, now);
    }

    private void leave(MapMember membership) {
        Optional<MapEntity> found = mapRepository.findById(membership.getMapId());
        if (found.isEmpty()) {
            // 지도는 없는데 참여 기록만 남은 경우. 정리만 한다.
            mapMemberRepository.delete(membership);
            return;
        }
        MapEntity map = found.get();
        if (membership.getRole() == MapRole.OWNER) {
            handOverOrDelete(map, membership);
            return;
        }
        mapMemberRepository.delete(membership);
        map.decreaseMemberCount();
    }

    private void handOverOrDelete(MapEntity map, MapMember owner) {
        Optional<MapMember> successor = map.isPersonal() ? Optional.empty() : nextOwner(map, owner);
        if (successor.isEmpty()) {
            delete(map);
            return;
        }
        MapMember next = successor.get();
        next.changeRole(MapRole.OWNER);
        map.transferOwnership(next.getUserId());
        mapMemberRepository.delete(owner);
        map.decreaseMemberCount();
        log.info("탈퇴한 방장의 지도를 위임했습니다. mapId={}, newOwnerId={}", map.getId(), next.getUserId());
    }

    private Optional<MapMember> nextOwner(MapEntity map, MapMember owner) {
        List<MapMember> others = mapMemberRepository.findByMapId(map.getId()).stream()
            .filter(member -> !member.getUserId().equals(owner.getUserId()))
            .toList();
        return others.stream().min(SUCCESSION_ORDER);
    }

    /**
     * 지도가 사라지면 그 안의 글은 남겨둘 곳이 없다. 예전 멤버가 남긴 글·댓글까지 지우고 지도를 지운다.
     * 지도를 하드 삭제하므로 글도 하드 삭제한다 — 소프트 삭제로 남기면 없는 지도를 가리키는 행만 쌓인다.
     */
    private void delete(MapEntity map) {
        mapPostCommentRepository.deleteAllByMapId(map.getId());
        mapPostRepository.deleteAll(mapPostRepository.findAllByMapId(map.getId()));
        mapMemberRepository.deleteByMapId(map.getId());
        mapRepository.delete(map);
    }
}
