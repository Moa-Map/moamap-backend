package com.moamap.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PlaceReportAdminResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceComment;
import com.moamap.place.entity.PlaceCommentReport;
import com.moamap.place.entity.PlaceReport;
import com.moamap.place.entity.PlaceReportTargetType;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.entity.ReportReason;
import com.moamap.place.repository.PlaceCommentReportRepository;
import com.moamap.place.repository.PlaceCommentRepository;
import com.moamap.place.repository.PlaceReportRepository;
import com.moamap.place.repository.PlaceRepository;
import com.moamap.place.user.UserAdminClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
@Import({PlaceReportAdminService.class, PlaceReportAdminReader.class})
class PlaceReportAdminServiceTest {

    private static final Long ADMIN = 100L;

    @Autowired private PlaceReportAdminService adminService;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceCommentRepository placeCommentRepository;
    @Autowired private PlaceReportRepository placeReportRepository;
    @Autowired private PlaceCommentReportRepository placeCommentReportRepository;
    @MockitoBean private UserAdminClient userAdminClient;

    @Test
    void 장소_신고는_신고자와_장소_내용_누적_신고_수를_함께_준다() {
        given(userAdminClient.isAdmin(ADMIN)).willReturn(true);
        Place place = place();
        placeReportRepository.saveAndFlush(PlaceReport.of(place.getId(), 10L, 11L, ReportReason.SEXUAL, "사진이 부적절해요"));
        placeReportRepository.saveAndFlush(PlaceReport.of(place.getId(), 10L, 12L, ReportReason.SPAM, null));

        List<PlaceReportAdminResponse> reports = adminService.getReports(ADMIN, PlaceReportTargetType.PLACE,
            PageRequest.of(0, 20)).getContent();

        assertThat(reports).hasSize(2).allSatisfy(report -> {
            assertThat(report.targetType()).isEqualTo(PlaceReportTargetType.PLACE);
            assertThat(report.targetReportCount()).isEqualTo(2);
            assertThat(report.content().title()).isEqualTo("성수 카페");
            assertThat(report.content().text()).isEqualTo("장소 메모");
            assertThat(report.content().imageUrls()).containsExactly("https://photos.example/place.jpg");
            assertThat(report.content().authorId()).isEqualTo(1L);
            assertThat(report.content().deleted()).isFalse();
        });
        // 최신순, 운영자는 신고자를 본다.
        assertThat(reports.get(0).reporterId()).isEqualTo(12L);
    }

    @Test
    void 댓글_신고는_댓글_내용과_달린_장소를_함께_준다() {
        given(userAdminClient.isAdmin(ADMIN)).willReturn(true);
        Place place = place();
        PlaceComment comment = placeCommentRepository.saveAndFlush(PlaceComment.builder()
            .placeId(place.getId()).userId(5L).rating(1).content("욕설 댓글")
            .imageUrls(new ArrayList<>(List.of("https://photos.example/comment.jpg"))).build());
        placeCommentReportRepository.saveAndFlush(PlaceCommentReport.of(comment.getId(), 11L, ReportReason.ABUSE, null));

        List<PlaceReportAdminResponse> reports = adminService.getReports(ADMIN, PlaceReportTargetType.PLACE_COMMENT,
            PageRequest.of(0, 20)).getContent();

        assertThat(reports).singleElement().satisfies(report -> {
            assertThat(report.targetId()).isEqualTo(comment.getId());
            assertThat(report.placeId()).isEqualTo(place.getId());
            assertThat(report.mapId()).isEqualTo(10L);
            assertThat(report.content().text()).isEqualTo("욕설 댓글");
            assertThat(report.content().title()).isEqualTo("성수 카페");
            assertThat(report.content().imageUrls()).containsExactly("https://photos.example/comment.jpg");
            assertThat(report.content().authorId()).isEqualTo(5L);
        });
    }

    @Test
    void 지워진_장소의_신고도_지워졌다고_표시해_보여준다() {
        given(userAdminClient.isAdmin(ADMIN)).willReturn(true);
        Place place = place();
        placeReportRepository.saveAndFlush(PlaceReport.of(place.getId(), 10L, 11L, ReportReason.SPAM, null));
        place.delete(1L);
        placeRepository.flush();

        assertThat(adminService.getReports(ADMIN, PlaceReportTargetType.PLACE, PageRequest.of(0, 20)).getContent())
            .singleElement().satisfies(report -> assertThat(report.content().deleted()).isTrue());
    }

    @Test
    void 운영자가_아니면_볼_수_없다() {
        given(userAdminClient.isAdmin(1L)).willReturn(false);

        assertThatThrownBy(() -> adminService.getReports(1L, PlaceReportTargetType.PLACE, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.ACCESS_DENIED);
    }

    private Place place() {
        return placeRepository.saveAndFlush(Place.builder()
            .name("성수 카페")
            .description("장소 메모")
            .lat(BigDecimal.valueOf(37.5))
            .lng(BigDecimal.valueOf(127.0))
            .kakaoPlaceId("A1")
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(10L)
            .createdBy(1L)
            .status(PlaceStatus.APPROVED)
            .photoUrls(new ArrayList<>(List.of("https://photos.example/place.jpg")))
            .build());
    }
}
