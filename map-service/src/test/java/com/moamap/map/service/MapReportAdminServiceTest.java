package com.moamap.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.dto.MapReportAdminResponse;
import com.moamap.map.entity.MapContentReport;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import com.moamap.map.entity.MapType;
import com.moamap.map.entity.ReportReason;
import com.moamap.map.entity.ReportTargetType;
import com.moamap.map.repository.MapContentReportRepository;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import com.moamap.map.user.UserAdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({JpaAuditingConfig.class, MapReportAdminService.class, MapReportAdminReader.class})
class MapReportAdminServiceTest {

    private static final Long ADMIN = 100L;
    private static final Long NOT_ADMIN = 200L;

    @Autowired private MapReportAdminService adminService;
    @Autowired private MapContentReportRepository reportRepository;
    @Autowired private MapEntityRepository mapRepository;
    @Autowired private MapPostRepository mapPostRepository;
    @Autowired private MapPostCommentRepository mapPostCommentRepository;
    @MockitoBean private UserAdminClient userAdminClient;

    @Test
    void 운영자는_신고와_신고된_내용_누적_신고_수를_함께_본다() {
        given(userAdminClient.isAdmin(ADMIN)).willReturn(true);
        MapEntity map = mapRepository.save(MapEntity.create("지도 이름", "지도 소개", "https://photos.example/cover.jpg",
            MapType.PRIVATE, 1L, List.of(), "ADMIN0001"));
        MapPost post = mapPostRepository.save(MapPost.create(map.getId(), 2L, "로그 본문",
            List.of("https://photos.example/post.jpg"), List.of()));
        MapPostComment comment = mapPostCommentRepository.save(MapPostComment.create(post.getId(), 3L, "댓글 본문"));
        report(ReportTargetType.MAP, map.getId(), map.getId(), 10L);
        report(ReportTargetType.MAP, map.getId(), map.getId(), 11L);
        report(ReportTargetType.MAP_POST, post.getId(), map.getId(), 10L);
        report(ReportTargetType.MAP_POST_COMMENT, comment.getId(), map.getId(), 10L);

        List<MapReportAdminResponse> reports = adminService.getReports(ADMIN, null, PageRequest.of(0, 20)).getContent();

        assertThat(reports).hasSize(4);
        assertThat(reports).filteredOn(r -> r.targetType() == ReportTargetType.MAP).allSatisfy(r -> {
            assertThat(r.targetReportCount()).isEqualTo(2);
            assertThat(r.content().title()).isEqualTo("지도 이름");
            assertThat(r.content().imageUrls()).containsExactly("https://photos.example/cover.jpg");
            assertThat(r.content().authorId()).isEqualTo(1L);
        });
        assertThat(reports).filteredOn(r -> r.targetType() == ReportTargetType.MAP_POST).singleElement().satisfies(r -> {
            assertThat(r.content().text()).isEqualTo("로그 본문");
            assertThat(r.content().imageUrls()).containsExactly("https://photos.example/post.jpg");
            assertThat(r.content().authorId()).isEqualTo(2L);
        });
        assertThat(reports).filteredOn(r -> r.targetType() == ReportTargetType.MAP_POST_COMMENT).singleElement()
            .satisfies(r -> assertThat(r.content().text()).isEqualTo("댓글 본문"));
    }

    @Test
    void 종류로_거를_수_있고_지워진_대상은_지워졌다고_표시한다() {
        given(userAdminClient.isAdmin(ADMIN)).willReturn(true);
        report(ReportTargetType.MAP, 9999L, 9999L, 10L);
        report(ReportTargetType.MAP_POST, 8888L, 9999L, 10L);

        List<MapReportAdminResponse> maps = adminService.getReports(ADMIN, ReportTargetType.MAP, PageRequest.of(0, 20))
            .getContent();

        assertThat(maps).singleElement().satisfies(r -> assertThat(r.content().deleted()).isTrue());
    }

    @Test
    void 운영자가_아니면_볼_수_없다() {
        given(userAdminClient.isAdmin(NOT_ADMIN)).willReturn(false);

        assertThatThrownBy(() -> adminService.getReports(NOT_ADMIN, null, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> adminService.getReports(null, null, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.ACCESS_DENIED);
    }

    private void report(ReportTargetType type, Long targetId, Long mapId, Long reporterId) {
        reportRepository.saveAndFlush(MapContentReport.of(type, targetId, mapId, reporterId, ReportReason.SEXUAL, null));
    }
}
