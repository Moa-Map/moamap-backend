package com.moamap.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.dto.MapMemberSummaryResponse;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.event.ActivitySignal;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.place.PlaceClient;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import com.moamap.map.user.UserClient;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 지도 나가기와 방장 위임 후보를 실제 DB로 확인한다. 나가기는 방장이 새 방장을 고르고, 회원 탈퇴는 자동으로 넘긴다(MapWithdrawalCleanupTest).
 */
@DataJpaTest
@RecordApplicationEvents
@Import({JpaAuditingConfig.class, MapService.class, MapOwnerSuccession.class})
class MapLeaveTest {

    private static final Long OWNER = 10L;
    private static final Long EARLY_MEMBER = 20L;
    private static final Long LATE_MEMBER = 30L;
    private static final Long EARLY_ADMIN = 40L;
    private static final Long LATE_ADMIN = 50L;
    private static final Long OUTSIDER = 99L;

    @Autowired private MapService mapService;
    @Autowired private MapEntityRepository mapRepository;
    @Autowired private MapMemberRepository mapMemberRepository;
    @Autowired private MapPostRepository mapPostRepository;
    @Autowired private MapPostCommentRepository mapPostCommentRepository;
    @Autowired private TestEntityManager em;
    @Autowired private ApplicationEvents events;

    @MockitoBean private InviteCodeGenerator inviteCodeGenerator;
    @MockitoBean private UserClient userClient;
    @MockitoBean private PlaceClient placeClient;

    private int inviteCodeSeq;

    @Test
    void 멤버가_나가면_빠지고_멤버_수가_줄며_방장은_그대로다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);

        Optional<Long> newOwner = leaveAndReload(map, EARLY_MEMBER, null);

        assertThat(newOwner).isEmpty();
        assertThat(mapMemberRepository.findByMapIdAndUserId(map.getId(), EARLY_MEMBER)).isEmpty();
        MapEntity reloaded = mapRepository.findById(map.getId()).orElseThrow();
        assertThat(reloaded.getMemberCount()).isEqualTo(1);
        assertThat(reloaded.getOwnerId()).isEqualTo(OWNER);
    }

    @Test
    void 관리자나_멤버가_새_방장을_보내도_무시하고_나가기만_한다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_ADMIN, MapRole.ADMIN);
        join(map, EARLY_MEMBER, MapRole.MEMBER);

        assertThat(leaveAndReload(map, EARLY_ADMIN, EARLY_MEMBER)).isEmpty();

        assertThat(mapRepository.findById(map.getId()).orElseThrow().getOwnerId()).isEqualTo(OWNER);
        assertThat(roleOf(map, EARLY_MEMBER)).isEqualTo(MapRole.MEMBER);
    }

    @Test
    void 방장이_고른_관리자에게_넘어가고_방장은_빠진다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, EARLY_ADMIN, MapRole.ADMIN);
        join(map, LATE_ADMIN, MapRole.ADMIN);

        // 먼저 참여한 관리자가 아니라 방장이 고른 사람에게 넘어간다.
        Optional<Long> newOwner = leaveAndReload(map, OWNER, LATE_ADMIN);

        assertThat(newOwner).contains(LATE_ADMIN);
        MapEntity reloaded = mapRepository.findById(map.getId()).orElseThrow();
        assertThat(reloaded.getOwnerId()).isEqualTo(LATE_ADMIN);
        assertThat(reloaded.getMemberCount()).isEqualTo(3);
        assertThat(roleOf(map, LATE_ADMIN)).isEqualTo(MapRole.OWNER);
        assertThat(roleOf(map, EARLY_ADMIN)).isEqualTo(MapRole.ADMIN);
        assertThat(roleOf(map, EARLY_MEMBER)).isEqualTo(MapRole.MEMBER);
        assertThat(mapMemberRepository.findByMapIdAndUserId(map.getId(), OWNER)).isEmpty();
    }

    @Test
    void 관리자가_없으면_방장이_고른_멤버에게_넘어간다() {
        MapEntity map = map(OWNER, MapType.PRIVATE);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, LATE_MEMBER, MapRole.MEMBER);

        assertThat(leaveAndReload(map, OWNER, LATE_MEMBER)).contains(LATE_MEMBER);
        assertThat(roleOf(map, LATE_MEMBER)).isEqualTo(MapRole.OWNER);
        assertThat(roleOf(map, EARLY_MEMBER)).isEqualTo(MapRole.MEMBER);
    }

    @Test
    void 새_방장을_고르지_않으면_나갈_수_없고_아무것도_바뀌지_않는다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);

        assertErrorCode(() -> mapService.leave(map.getId(), OWNER, null), MapErrorCode.OWNER_HANDOVER_REQUIRED);

        assertNothingChanged(map, 2);
    }

    @Test
    void 관리자가_있으면_일반_멤버에게는_넘길_수_없다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, EARLY_ADMIN, MapRole.ADMIN);

        assertErrorCode(() -> mapService.leave(map.getId(), OWNER, EARLY_MEMBER), MapErrorCode.NOT_OWNER_CANDIDATE);

        assertNothingChanged(map, 3);
        assertThat(roleOf(map, EARLY_MEMBER)).isEqualTo(MapRole.MEMBER);
    }

    @Test
    void 지도_멤버가_아니거나_자기_자신은_새_방장으로_고를_수_없다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);

        assertErrorCode(() -> mapService.leave(map.getId(), OWNER, OUTSIDER), MapErrorCode.NOT_OWNER_CANDIDATE);
        assertErrorCode(() -> mapService.leave(map.getId(), OWNER, OWNER), MapErrorCode.NOT_OWNER_CANDIDATE);

        assertNothingChanged(map, 2);
    }

    @Test
    void 방장이_나가서_넘어가면_새_방장에게_알림을_보낸다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);

        leaveAndReload(map, OWNER, EARLY_MEMBER);

        assertThat(events.stream(ActivitySignal.class)).containsExactly(
            new ActivitySignal(ActivitySignal.MAP_OWNER_TRANSFERRED, EARLY_MEMBER, null, map.getId(), map.getId()));
    }

    @Test
    void 멤버가_나가거나_방장_나가기가_막히면_알림을_보내지_않는다() {
        MapEntity shared = map(OWNER, MapType.COMMUNITY);
        join(shared, EARLY_MEMBER, MapRole.MEMBER);
        MapEntity solo = map(LATE_ADMIN, MapType.COMMUNITY);

        assertErrorCode(() -> mapService.leave(shared.getId(), OWNER, null), MapErrorCode.OWNER_HANDOVER_REQUIRED);
        leaveAndReload(shared, EARLY_MEMBER, null);
        assertErrorCode(() -> mapService.leave(solo.getId(), LATE_ADMIN, null), MapErrorCode.SOLE_OWNER_CANNOT_LEAVE);

        assertThat(events.stream(ActivitySignal.class)).isEmpty();
    }

    @Test
    void 혼자_남은_방장은_나갈_수_없고_위임보다_지도_삭제를_먼저_안내한다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);

        assertErrorCode(() -> mapService.leave(map.getId(), OWNER, null), MapErrorCode.SOLE_OWNER_CANNOT_LEAVE);
        assertErrorCode(() -> mapService.leave(map.getId(), OWNER, OUTSIDER), MapErrorCode.SOLE_OWNER_CANNOT_LEAVE);

        assertNothingChanged(map, 1);
    }

    @Test
    void 나만의_지도는_나갈_수_없다() {
        MapEntity personal = mapRepository.save(MapEntity.createPersonal(OWNER, "나만의 지도"));
        mapMemberRepository.save(MapMember.of(personal.getId(), OWNER, MapRole.OWNER));

        assertErrorCode(() -> mapService.leave(personal.getId(), OWNER, null), MapErrorCode.CANNOT_LEAVE_PERSONAL_MAP);
    }

    @Test
    void 방장이_나가도_쓴_글과_댓글은_그대로_남는다() {
        // 회원 탈퇴와 다른 점이다. 탈퇴는 글·댓글을 숨기지만, 나가기는 지도에 남긴 기록을 건드리지 않는다.
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        MapPost post = mapPostRepository.save(MapPost.create(map.getId(), OWNER, "방장의 글", List.of(), List.of()));
        MapPostComment comment = mapPostCommentRepository.save(MapPostComment.create(post.getId(), OWNER, "방장의 댓글"));

        leaveAndReload(map, OWNER, EARLY_MEMBER);

        assertThat(mapPostRepository.findById(post.getId()).orElseThrow().getDeletedAt()).isNull();
        assertThat(mapPostCommentRepository.findById(comment.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void 멤버가_아니거나_없는_지도면_나갈_수_없다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);

        assertErrorCode(() -> mapService.leave(map.getId(), OUTSIDER, null), MapErrorCode.NOT_MAP_MEMBER);
        assertErrorCode(() -> mapService.leave(9999L, OWNER, null), MapErrorCode.MAP_NOT_FOUND);
    }

    @Test
    void 위임_후보는_관리자가_있으면_관리자만_먼저_참여한_순으로_나온다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, EARLY_ADMIN, MapRole.ADMIN);
        join(map, LATE_ADMIN, MapRole.ADMIN);

        assertThat(mapService.getOwnerCandidates(map.getId(), OWNER))
            .extracting(MapMemberSummaryResponse::userId)
            .containsExactly(EARLY_ADMIN, LATE_ADMIN);
    }

    @Test
    void 위임_후보는_관리자가_없으면_일반_멤버_전체다() {
        MapEntity map = map(OWNER, MapType.PRIVATE);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, LATE_MEMBER, MapRole.MEMBER);

        assertThat(mapService.getOwnerCandidates(map.getId(), OWNER))
            .extracting(MapMemberSummaryResponse::userId)
            .containsExactly(EARLY_MEMBER, LATE_MEMBER);
    }

    @Test
    void 위임_후보는_혼자_남은_지도면_비어_있고_방장만_볼_수_있다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);

        assertThat(mapService.getOwnerCandidates(map.getId(), OWNER)).isEmpty();

        join(map, EARLY_ADMIN, MapRole.ADMIN);
        assertErrorCode(() -> mapService.getOwnerCandidates(map.getId(), EARLY_ADMIN), MapErrorCode.NOT_MAP_OWNER);
    }

    private void assertNothingChanged(MapEntity map, int memberCount) {
        em.clear();
        MapEntity reloaded = mapRepository.findById(map.getId()).orElseThrow();
        assertThat(reloaded.getOwnerId()).isEqualTo(OWNER);
        assertThat(reloaded.getMemberCount()).isEqualTo(memberCount);
        assertThat(roleOf(map, OWNER)).isEqualTo(MapRole.OWNER);
    }

    private Optional<Long> leaveAndReload(MapEntity map, Long userId, Long newOwnerId) {
        Optional<Long> newOwner = mapService.leave(map.getId(), userId, newOwnerId);
        em.flush();
        em.clear();
        return newOwner;
    }

    /** 생성자는 OWNER로 참여한다(MapService.create와 같은 상태). */
    private MapEntity map(Long ownerId, MapType type) {
        String inviteCode = type == MapType.PRIVATE ? "LEAVE%05d".formatted(++inviteCodeSeq) : null;
        MapEntity map = mapRepository.save(MapEntity.create("지도", null, null, type, ownerId, List.of(), inviteCode));
        mapMemberRepository.save(MapMember.of(map.getId(), ownerId, MapRole.OWNER));
        em.flush();
        return map;
    }

    private void join(MapEntity map, Long userId, MapRole role) {
        mapMemberRepository.save(MapMember.of(map.getId(), userId, role));
        MapEntity managed = mapRepository.findById(map.getId()).orElseThrow();
        managed.increaseMemberCount();
        em.flush();
    }

    private MapRole roleOf(MapEntity map, Long userId) {
        return mapMemberRepository.findByMapIdAndUserId(map.getId(), userId).orElseThrow().getRole();
    }

    private static void assertErrorCode(ThrowingCallable call, MapErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", code);
    }
}
