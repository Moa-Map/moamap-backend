package com.moamap.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.dto.ReportRequest;
import com.moamap.map.entity.MapContentReport;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.entity.ReportReason;
import com.moamap.map.entity.ReportTargetType;
import com.moamap.map.event.ReportFiled;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapContentReportRepository;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 지도·로그·로그 댓글 신고 규칙을 실제 DB로 확인한다.
 */
@DataJpaTest
@RecordApplicationEvents
@Import({JpaAuditingConfig.class, MapReportService.class, MapPostAccessPolicy.class})
class MapReportServiceTest {

    private static final Long OWNER = 1L;
    private static final Long MEMBER = 2L;
    private static final Long OUTSIDER = 3L;

    @Autowired private MapReportService mapReportService;
    @Autowired private MapEntityRepository mapRepository;
    @Autowired private MapMemberRepository mapMemberRepository;
    @Autowired private MapPostRepository mapPostRepository;
    @Autowired private MapPostCommentRepository mapPostCommentRepository;
    @Autowired private MapContentReportRepository reportRepository;
    @Autowired private ApplicationEvents events;

    private int inviteCodeSeq;

    @Test
    void 커뮤니티_지도는_멤버가_아니어도_신고할_수_있고_운영자_알림_신호를_남긴다() {
        MapEntity map = map(MapType.COMMUNITY);

        Long reportId = mapReportService.reportMap(map.getId(), OUTSIDER, request(ReportReason.SEXUAL)).reportId();

        MapContentReport saved = reportRepository.findById(reportId).orElseThrow();
        assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.MAP);
        assertThat(saved.getTargetId()).isEqualTo(map.getId());
        assertThat(saved.getReporterId()).isEqualTo(OUTSIDER);
        assertThat(events.stream(ReportFiled.class)).containsExactly(new ReportFiled(reportId, map.getId()));
    }

    @Test
    void 프라이빗_지도는_멤버만_신고할_수_있다() {
        MapEntity map = map(MapType.PRIVATE);
        join(map, MEMBER);

        assertErrorCode(() -> mapReportService.reportMap(map.getId(), OUTSIDER, request(ReportReason.SPAM)),
            MapErrorCode.NOT_MAP_MEMBER);
        assertThat(mapReportService.reportMap(map.getId(), MEMBER, request(ReportReason.SPAM)).reportId()).isNotNull();
    }

    @Test
    void 내_지도와_공식_지도는_신고할_수_없다() {
        MapEntity mine = map(MapType.COMMUNITY);
        MapEntity official = map(MapType.OFFICIAL);

        assertErrorCode(() -> mapReportService.reportMap(mine.getId(), OWNER, request(ReportReason.SPAM)),
            MapErrorCode.CANNOT_REPORT_OWN_CONTENT);
        assertErrorCode(() -> mapReportService.reportMap(official.getId(), OUTSIDER, request(ReportReason.SPAM)),
            MapErrorCode.OFFICIAL_MAP_NOT_REPORTABLE);
    }

    @Test
    void 같은_대상을_두_번_신고할_수_없다() {
        MapEntity map = map(MapType.COMMUNITY);
        mapReportService.reportMap(map.getId(), OUTSIDER, request(ReportReason.SPAM));

        assertErrorCode(() -> mapReportService.reportMap(map.getId(), OUTSIDER, request(ReportReason.ABUSE)),
            MapErrorCode.ALREADY_REPORTED);
        assertThat(reportRepository.findAll()).hasSize(1);
    }

    @Test
    void 커뮤니티_지도의_로그는_누구나_신고하고_내_로그는_신고할_수_없다() {
        MapEntity map = map(MapType.COMMUNITY);
        MapPost post = post(map, MEMBER);

        assertThat(mapReportService.reportPost(map.getId(), post.getId(), OUTSIDER, request(ReportReason.ABUSE)).reportId())
            .isNotNull();
        assertErrorCode(() -> mapReportService.reportPost(map.getId(), post.getId(), MEMBER, request(ReportReason.ABUSE)),
            MapErrorCode.CANNOT_REPORT_OWN_CONTENT);
    }

    @Test
    void 프라이빗_지도의_로그는_멤버만_신고할_수_있다() {
        MapEntity map = map(MapType.PRIVATE);
        join(map, MEMBER);
        MapPost post = post(map, OWNER);

        assertErrorCode(() -> mapReportService.reportPost(map.getId(), post.getId(), OUTSIDER, request(ReportReason.SPAM)),
            MapErrorCode.NOT_MAP_MEMBER);
    }

    @Test
    void 다른_지도의_로그거나_지워진_로그는_없는_것으로_다룬다() {
        MapEntity map = map(MapType.COMMUNITY);
        MapEntity other = map(MapType.COMMUNITY);
        MapPost postInOther = post(other, MEMBER);
        MapPost deleted = post(map, MEMBER);
        deleted.delete();
        mapPostRepository.flush();

        assertErrorCode(() -> mapReportService.reportPost(map.getId(), postInOther.getId(), OUTSIDER, request(ReportReason.SPAM)),
            MapErrorCode.MAP_POST_NOT_FOUND);
        assertErrorCode(() -> mapReportService.reportPost(map.getId(), deleted.getId(), OUTSIDER, request(ReportReason.SPAM)),
            MapErrorCode.MAP_POST_NOT_FOUND);
    }

    @Test
    void 로그_댓글을_신고하고_내_댓글이나_다른_로그의_댓글은_신고할_수_없다() {
        MapEntity map = map(MapType.COMMUNITY);
        MapPost post = post(map, OWNER);
        MapPost otherPost = post(map, OWNER);
        MapPostComment comment = mapPostCommentRepository.saveAndFlush(MapPostComment.create(post.getId(), MEMBER, "댓글"));

        Long reportId = mapReportService.reportComment(map.getId(), post.getId(), comment.getId(), OUTSIDER,
            request(ReportReason.ABUSE)).reportId();

        assertThat(reportRepository.findById(reportId).orElseThrow().getTargetType())
            .isEqualTo(ReportTargetType.MAP_POST_COMMENT);
        assertErrorCode(() -> mapReportService.reportComment(map.getId(), post.getId(), comment.getId(), MEMBER,
            request(ReportReason.ABUSE)), MapErrorCode.CANNOT_REPORT_OWN_CONTENT);
        assertErrorCode(() -> mapReportService.reportComment(map.getId(), otherPost.getId(), comment.getId(), OUTSIDER,
            request(ReportReason.ABUSE)), MapErrorCode.MAP_POST_COMMENT_NOT_FOUND);
    }

    private MapEntity map(MapType type) {
        String inviteCode = type == MapType.PRIVATE ? "RPT%05d".formatted(++inviteCodeSeq) : null;
        MapEntity map = mapRepository.save(MapEntity.create("지도", "소개", null, type, OWNER, List.of(), inviteCode));
        mapMemberRepository.save(MapMember.of(map.getId(), OWNER, MapRole.OWNER));
        return map;
    }

    private void join(MapEntity map, Long userId) {
        mapMemberRepository.saveAndFlush(MapMember.of(map.getId(), userId, MapRole.MEMBER));
    }

    private MapPost post(MapEntity map, Long writerId) {
        return mapPostRepository.saveAndFlush(MapPost.create(map.getId(), writerId, "로그", List.of(), List.of()));
    }

    private static ReportRequest request(ReportReason reason) {
        return new ReportRequest(reason, null);
    }

    private static void assertErrorCode(ThrowingCallable call, MapErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }
}
