package com.moamap.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.ReportedPlaceResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceReport;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.entity.ReportReason;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceReportRepository;
import com.moamap.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
@Import(ReportedPlaceService.class)
class ReportedPlaceServiceTest {

    private static final Long MAP_ID = 10L;
    private static final Long OTHER_MAP_ID = 20L;
    private static final Long MANAGER = 1L;

    @Autowired private ReportedPlaceService reportedPlaceService;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceReportRepository placeReportRepository;
    @MockitoBean private MapClient mapClient;

    private int kakaoIdSeq;

    @Test
    void 신고_많은_순으로_사유별_수와_함께_보여주고_신고자는_담지_않는다() {
        givenRole(MapMemberRole.OWNER);
        Place many = place(MAP_ID, "신고 많은 곳", List.of("https://photos.example/a.jpg"));
        Place few = place(MAP_ID, "신고 적은 곳", List.of());
        report(many, 11L, ReportReason.FALSE_INFO);
        report(many, 12L, ReportReason.FALSE_INFO);
        report(many, 13L, ReportReason.SPAM);
        report(few, 11L, ReportReason.SEXUAL);

        List<ReportedPlaceResponse> result = reportedPlaceService.findReportedPlaces(MAP_ID, MANAGER, PageRequest.of(0, 20))
            .content();

        assertThat(result).extracting(ReportedPlaceResponse::placeId).containsExactly(many.getId(), few.getId());
        assertThat(result.get(0).reportCount()).isEqualTo(3);
        assertThat(result.get(0).reasons()).isEqualTo(Map.of(ReportReason.FALSE_INFO, 2L, ReportReason.SPAM, 1L));
        assertThat(result.get(0).photoUrl()).isEqualTo("https://photos.example/a.jpg");
        assertThat(result.get(1).photoUrl()).isNull();
        assertThat(ReportedPlaceResponse.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName).doesNotContain("reporterId");
    }

    @Test
    void 다른_지도의_신고와_이미_삭제한_장소는_빠진다() {
        givenRole(MapMemberRole.ADMIN);
        Place deleted = place(MAP_ID, "삭제한 곳", List.of());
        report(deleted, 11L, ReportReason.SPAM);
        deleted.delete(MANAGER);
        placeRepository.flush();
        report(place(OTHER_MAP_ID, "다른 지도", List.of()), 11L, ReportReason.SPAM);

        assertThat(reportedPlaceService.findReportedPlaces(MAP_ID, MANAGER, PageRequest.of(0, 20)).content()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = MapMemberRole.class, names = {"MEMBER", "NONE"})
    void 방장_관리자가_아니면_볼_수_없다(MapMemberRole role) {
        givenRole(role);

        assertThatThrownBy(() -> reportedPlaceService.findReportedPlaces(MAP_ID, MANAGER, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.NOT_REVIEWER);
    }

    private void givenRole(MapMemberRole role) {
        given(mapClient.getMemberInfo(MAP_ID, MANAGER)).willReturn(new MapMemberResponse(MapType.COMMUNITY, role, true));
    }

    private Place place(Long mapId, String name, List<String> photoUrls) {
        return placeRepository.saveAndFlush(Place.builder()
            .name(name)
            .lat(BigDecimal.valueOf(37.5))
            .lng(BigDecimal.valueOf(127.0))
            .kakaoPlaceId("R" + (++kakaoIdSeq))
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(mapId)
            .createdBy(MANAGER)
            .status(PlaceStatus.APPROVED)
            .photoUrls(new java.util.ArrayList<>(photoUrls))
            .build());
    }

    private void report(Place place, Long reporterId, ReportReason reason) {
        placeReportRepository.saveAndFlush(PlaceReport.of(place.getId(), place.getMapId(), reporterId, reason, null));
    }
}
