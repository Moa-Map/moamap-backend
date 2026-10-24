package com.moamap.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.config.JpaAuditingConfig;
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
 * 지도 나가기를 실제 DB로 확인한다. 방장 위임 순서는 회원 탈퇴와 같아야 한다(MapWithdrawalCleanupTest).
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

        Optional<Long> newOwner = leaveAndReload(map, EARLY_MEMBER);

        assertThat(newOwner).isEmpty();
        assertThat(mapMemberRepository.findByMapIdAndUserId(map.getId(), EARLY_MEMBER)).isEmpty();
        MapEntity reloaded = mapRepository.findById(map.getId()).orElseThrow();
        assertThat(reloaded.getMemberCount()).isEqualTo(1);
        assertThat(reloaded.getOwnerId()).isEqualTo(OWNER);
    }

    @Test
    void 방장이_나가면_먼저_들어온_멤버보다_관리자에게_넘어가고_관리자끼리는_먼저_참여한_사람이다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, EARLY_ADMIN, MapRole.ADMIN);
        join(map, LATE_ADMIN, MapRole.ADMIN);

        Optional<Long> newOwner = leaveAndReload(map, OWNER);

        assertThat(newOwner).contains(EARLY_ADMIN);
        MapEntity reloaded = mapRepository.findById(map.getId()).orElseThrow();
        assertThat(reloaded.getOwnerId()).isEqualTo(EARLY_ADMIN);
        assertThat(reloaded.getMemberCount()).isEqualTo(3);
        assertThat(roleOf(map, EARLY_ADMIN)).isEqualTo(MapRole.OWNER);
        assertThat(roleOf(map, LATE_ADMIN)).isEqualTo(MapRole.ADMIN);
        assertThat(roleOf(map, EARLY_MEMBER)).isEqualTo(MapRole.MEMBER);
        assertThat(mapMemberRepository.findByMapIdAndUserId(map.getId(), OWNER)).isEmpty();
    }

    @Test
    void 관리자가_없으면_가장_먼저_참여한_멤버에게_넘어간다() {
        MapEntity map = map(OWNER, MapType.PRIVATE);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, LATE_MEMBER, MapRole.MEMBER);

        assertThat(leaveAndReload(map, OWNER)).contains(EARLY_MEMBER);
        assertThat(roleOf(map, EARLY_MEMBER)).isEqualTo(MapRole.OWNER);
        assertThat(roleOf(map, LATE_MEMBER)).isEqualTo(MapRole.MEMBER);
    }

    @Test
    void 새_방장도_나가면_그다음_사람에게_넘어간다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, LATE_MEMBER, MapRole.MEMBER);

        leaveAndReload(map, OWNER);

        assertThat(leaveAndReload(map, EARLY_MEMBER)).contains(LATE_MEMBER);
        assertThat(mapRepository.findById(map.getId()).orElseThrow().getMemberCount()).isEqualTo(1);
    }

    @Test
    void 방장이_나가서_넘어가면_새_방장에게_알림을_보낸다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);

        leaveAndReload(map, OWNER);

        assertThat(events.stream(ActivitySignal.class)).containsExactly(
            new ActivitySignal(ActivitySignal.MAP_OWNER_TRANSFERRED, EARLY_MEMBER, null, map.getId(), map.getId()));
    }

    @Test
    void 멤버가_나가거나_혼자_남은_방장이_막히면_알림을_보내지_않는다() {
        MapEntity shared = map(OWNER, MapType.COMMUNITY);
        join(shared, EARLY_MEMBER, MapRole.MEMBER);
        MapEntity solo = map(LATE_ADMIN, MapType.COMMUNITY);

        leaveAndReload(shared, EARLY_MEMBER);
        assertErrorCode(() -> mapService.leave(solo.getId(), LATE_ADMIN), MapErrorCode.SOLE_OWNER_CANNOT_LEAVE);

        assertThat(events.stream(ActivitySignal.class)).isEmpty();
    }

    @Test
    void 혼자_남은_방장은_나갈_수_없고_아무것도_바뀌지_않는다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);

        assertErrorCode(() -> mapService.leave(map.getId(), OWNER), MapErrorCode.SOLE_OWNER_CANNOT_LEAVE);

        em.clear();
        assertThat(mapRepository.findById(map.getId())).isPresent();
        assertThat(roleOf(map, OWNER)).isEqualTo(MapRole.OWNER);
    }

    @Test
    void 나만의_지도는_나갈_수_없다() {
        MapEntity personal = mapRepository.save(MapEntity.createPersonal(OWNER, "나만의 지도"));
        mapMemberRepository.save(MapMember.of(personal.getId(), OWNER, MapRole.OWNER));

        assertErrorCode(() -> mapService.leave(personal.getId(), OWNER), MapErrorCode.CANNOT_LEAVE_PERSONAL_MAP);
    }

    @Test
    void 방장이_나가도_쓴_글과_댓글은_그대로_남는다() {
        // 회원 탈퇴와 다른 점이다. 탈퇴는 글·댓글을 숨기지만, 나가기는 지도에 남긴 기록을 건드리지 않는다.
        MapEntity map = map(OWNER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        MapPost post = mapPostRepository.save(MapPost.create(map.getId(), OWNER, "방장의 글", List.of(), List.of()));
        MapPostComment comment = mapPostCommentRepository.save(MapPostComment.create(post.getId(), OWNER, "방장의 댓글"));

        leaveAndReload(map, OWNER);

        assertThat(mapPostRepository.findById(post.getId()).orElseThrow().getDeletedAt()).isNull();
        assertThat(mapPostCommentRepository.findById(comment.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void 멤버가_아니거나_없는_지도면_나갈_수_없다() {
        MapEntity map = map(OWNER, MapType.COMMUNITY);

        assertErrorCode(() -> mapService.leave(map.getId(), OUTSIDER), MapErrorCode.NOT_MAP_MEMBER);
        assertErrorCode(() -> mapService.leave(9999L, OWNER), MapErrorCode.MAP_NOT_FOUND);
    }

    private Optional<Long> leaveAndReload(MapEntity map, Long userId) {
        Optional<Long> newOwner = mapService.leave(map.getId(), userId);
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
