package com.moamap.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.entity.PlaceTag;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * 탈퇴 회원의 지도 데이터 정리. 방장 위임 순서와 멤버 수, 글 삭제 범위를 실제 쿼리로 확인한다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, MapWithdrawalCleanupService.class})
class MapWithdrawalCleanupTest {

    private static final Long LEAVER = 10L;
    private static final Long EARLY_MEMBER = 20L;
    private static final Long LATE_MEMBER = 30L;
    private static final Long ADMIN = 40L;

    @Autowired private MapWithdrawalCleanupService cleanupService;
    @Autowired private MapEntityRepository mapRepository;
    @Autowired private MapMemberRepository mapMemberRepository;
    @Autowired private MapPostRepository mapPostRepository;
    @Autowired private MapPostCommentRepository mapPostCommentRepository;
    @Autowired private TestEntityManager em;

    private int inviteCodeSeq;

    @Test
    void 남의_지도에서는_빠지고_멤버_수가_줄어든다() {
        MapEntity map = map(EARLY_MEMBER, MapType.COMMUNITY);
        join(map, LEAVER, MapRole.MEMBER);

        cleanUpAndReload(LEAVER);

        assertThat(mapMemberRepository.findByMapIdAndUserId(map.getId(), LEAVER)).isEmpty();
        assertThat(mapRepository.findById(map.getId()).orElseThrow().getMemberCount()).isEqualTo(1);
    }

    @Test
    void 방장이_탈퇴하면_먼저_들어온_멤버보다_관리자에게_먼저_넘어간다() {
        MapEntity map = map(LEAVER, MapType.PRIVATE);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, ADMIN, MapRole.ADMIN);

        cleanUpAndReload(LEAVER);

        MapEntity reloaded = mapRepository.findById(map.getId()).orElseThrow();
        assertThat(reloaded.getOwnerId()).isEqualTo(ADMIN);
        assertThat(roleOf(map, ADMIN)).isEqualTo(MapRole.OWNER);
        assertThat(roleOf(map, EARLY_MEMBER)).isEqualTo(MapRole.MEMBER);
        assertThat(reloaded.getMemberCount()).isEqualTo(2);
    }

    @Test
    void 관리자가_없으면_가장_먼저_들어온_멤버에게_넘어간다() {
        MapEntity map = map(LEAVER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        join(map, LATE_MEMBER, MapRole.MEMBER);

        cleanUpAndReload(LEAVER);

        assertThat(mapRepository.findById(map.getId()).orElseThrow().getOwnerId()).isEqualTo(EARLY_MEMBER);
        assertThat(roleOf(map, EARLY_MEMBER)).isEqualTo(MapRole.OWNER);
        assertThat(roleOf(map, LATE_MEMBER)).isEqualTo(MapRole.MEMBER);
    }

    @Test
    void 혼자_쓰던_지도는_지운다() {
        MapEntity map = map(LEAVER, MapType.PRIVATE);

        cleanUpAndReload(LEAVER);

        assertThat(mapRepository.findById(map.getId())).isEmpty();
        assertThat(mapMemberRepository.findByMapId(map.getId())).isEmpty();
    }

    @Test
    void 나만의_지도는_지운다() {
        MapEntity personal = mapRepository.save(MapEntity.createPersonal(LEAVER, "나만의 지도"));
        mapMemberRepository.save(MapMember.of(personal.getId(), LEAVER, MapRole.OWNER));

        cleanUpAndReload(LEAVER);

        assertThat(mapRepository.findById(personal.getId())).isEmpty();
    }

    @Test
    void 지도를_지울_때_예전_멤버가_남긴_글과_댓글도_함께_지운다() {
        // 예전 멤버가 글을 남기고 나간 뒤, 방장이 혼자 남은 상태에서 탈퇴하는 경우다.
        MapEntity map = map(LEAVER, MapType.COMMUNITY);
        MapPost leftBehind = mapPostRepository.save(MapPost.create(map.getId(), EARLY_MEMBER, "나간 멤버의 글",
            List.of("https://photos.example.com/map-posts/1/a.jpg"), List.of(new PlaceTag(1L, "장소"))));
        mapPostCommentRepository.save(MapPostComment.create(leftBehind.getId(), LATE_MEMBER, "댓글"));
        MapEntity otherMap = map(EARLY_MEMBER, MapType.COMMUNITY);
        MapPost untouched = mapPostRepository.save(MapPost.create(otherMap.getId(), EARLY_MEMBER, "다른 지도 글",
            List.of("https://photos.example.com/map-posts/2/b.jpg"), List.of()));
        em.flush();

        cleanUpAndReload(LEAVER);

        assertThat(mapRepository.findById(map.getId())).isEmpty();
        assertThat(mapPostRepository.findById(leftBehind.getId())).isEmpty();
        assertThat(mapPostCommentRepository.findAll()).noneMatch(c -> c.getMapPostId().equals(leftBehind.getId()));
        // 게시글을 엔티티로 지워야 컬렉션 테이블 행도 같이 사라진다. 남으면 없는 게시글을 가리키는 행이 된다.
        assertThat(countRows("map_post_images", leftBehind.getId())).isZero();
        assertThat(countRows("map_post_place_tags", leftBehind.getId())).isZero();
        // 다른 지도의 글은 건드리지 않는다.
        assertThat(mapPostRepository.findById(untouched.getId())).isPresent();
        assertThat(countRows("map_post_images", untouched.getId())).isEqualTo(1);
    }

    @Test
    void 쓴_게시글과_댓글을_지우고_남의_글은_그대로_둔다() {
        MapEntity map = map(EARLY_MEMBER, MapType.COMMUNITY);
        join(map, LEAVER, MapRole.MEMBER);
        MapPost myPost = mapPostRepository.save(MapPost.create(map.getId(), LEAVER, "내 글", List.of(), List.of()));
        MapPost othersPost = mapPostRepository.save(MapPost.create(map.getId(), EARLY_MEMBER, "남의 글", List.of(), List.of()));
        MapPostComment myComment = mapPostCommentRepository.save(MapPostComment.create(othersPost.getId(), LEAVER, "내 댓글"));
        MapPostComment othersComment = mapPostCommentRepository.save(
            MapPostComment.create(othersPost.getId(), EARLY_MEMBER, "남의 댓글"));

        cleanUpAndReload(LEAVER);

        assertThat(mapPostRepository.findByIdAndDeletedAtIsNull(myPost.getId())).isEmpty();
        assertThat(mapPostCommentRepository.findByIdAndDeletedAtIsNull(myComment.getId())).isEmpty();
        assertThat(mapPostRepository.findByIdAndDeletedAtIsNull(othersPost.getId())).isPresent();
        assertThat(mapPostCommentRepository.findByIdAndDeletedAtIsNull(othersComment.getId())).isPresent();
    }

    @Test
    void 같은_이벤트가_다시_와도_결과가_같다() {
        MapEntity map = map(LEAVER, MapType.COMMUNITY);
        join(map, EARLY_MEMBER, MapRole.MEMBER);
        cleanUpAndReload(LEAVER);

        assertThatCode(() -> cleanUpAndReload(LEAVER)).doesNotThrowAnyException();

        MapEntity reloaded = mapRepository.findById(map.getId()).orElseThrow();
        assertThat(reloaded.getOwnerId()).isEqualTo(EARLY_MEMBER);
        assertThat(reloaded.getMemberCount()).isEqualTo(1);
    }

    private void cleanUpAndReload(Long userId) {
        cleanupService.cleanUp(userId);
        em.flush();
        em.clear();
    }

    /** 생성자는 OWNER로 참여한다(MapService.create와 같은 상태). */
    private MapEntity map(Long ownerId, MapType type) {
        String inviteCode = type == MapType.PRIVATE ? "CODE%06d".formatted(++inviteCodeSeq) : null;
        MapEntity map = mapRepository.save(MapEntity.create("지도", null, null, type, ownerId, List.of(), inviteCode));
        mapMemberRepository.save(MapMember.of(map.getId(), ownerId, MapRole.OWNER));
        return map;
    }

    private void join(MapEntity map, Long userId, MapRole role) {
        mapMemberRepository.save(MapMember.of(map.getId(), userId, role));
        map.increaseMemberCount();
        em.flush();
    }

    private long countRows(String table, Long mapPostId) {
        return ((Number) em.getEntityManager()
            .createNativeQuery("select count(*) from " + table + " where map_post_id = :id")
            .setParameter("id", mapPostId)
            .getSingleResult()).longValue();
    }

    private MapRole roleOf(MapEntity map, Long userId) {
        return mapMemberRepository.findByMapIdAndUserId(map.getId(), userId).orElseThrow().getRole();
    }
}
