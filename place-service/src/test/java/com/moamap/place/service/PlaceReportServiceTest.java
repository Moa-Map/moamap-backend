package com.moamap.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.exception.ErrorCode;
import com.moamap.place.dto.ReportRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceReport;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.entity.ReportReason;
import com.moamap.place.event.ReportFiled;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceReportRepository;
import com.moamap.place.repository.PlaceRepository;
import java.math.BigDecimal;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 장소 신고 규칙을 실제 DB로 확인한다. 지도 정보(유형·역할)는 map-service 응답을 흉내 낸다.
 */
@DataJpaTest
@RecordApplicationEvents
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
@Import({PlaceReportService.class, MapViewPolicy.class})
class PlaceReportServiceTest {

    private static final Long MAP_ID = 10L;
    private static final Long CREATOR = 1L;
    private static final Long REPORTER = 2L;
    private static final Long OTHER_REPORTER = 3L;

    @Autowired private PlaceReportService placeReportService;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private PlaceReportRepository placeReportRepository;
    @Autowired private ApplicationEvents events;
    @MockitoBean private MapClient mapClient;

    private int kakaoIdSeq;

    @Test
    void 커뮤니티_지도의_장소는_멤버가_아니어도_신고할_수_있고_첫_신고는_방장_관리자에게도_알린다() {
        Place place = place(PlaceStatus.APPROVED);
        given(MapType.COMMUNITY, MapMemberRole.NONE, REPORTER);

        Long reportId = placeReportService.report(place.getId(), REPORTER, request(ReportReason.FALSE_INFO)).reportId();

        PlaceReport saved = placeReportRepository.findById(reportId).orElseThrow();
        assertThat(saved.getPlaceId()).isEqualTo(place.getId());
        assertThat(saved.getMapId()).isEqualTo(MAP_ID);
        assertThat(saved.getReason()).isEqualTo(ReportReason.FALSE_INFO);
        assertThat(events.stream(ReportFiled.class)).containsExactly(
            ReportFiled.toOperatorsAndManagers(reportId, MAP_ID, REPORTER, place.getId()));
    }

    @Test
    void 같은_장소의_두_번째_신고부터는_운영자에게만_알린다() {
        Place place = place(PlaceStatus.APPROVED);
        given(MapType.COMMUNITY, MapMemberRole.NONE, REPORTER);
        given(MapType.COMMUNITY, MapMemberRole.NONE, OTHER_REPORTER);
        placeReportService.report(place.getId(), REPORTER, request(ReportReason.SPAM));

        Long second = placeReportService.report(place.getId(), OTHER_REPORTER, request(ReportReason.SPAM)).reportId();

        assertThat(events.stream(ReportFiled.class)).last()
            .isEqualTo(ReportFiled.toOperators(second, MAP_ID, OTHER_REPORTER));
    }

    @Test
    void 공식_지도의_장소는_운영자에게만_알린다() {
        Place place = place(PlaceStatus.APPROVED);
        given(MapType.OFFICIAL, MapMemberRole.NONE, REPORTER);

        Long reportId = placeReportService.report(place.getId(), REPORTER, request(ReportReason.FALSE_INFO)).reportId();

        assertThat(events.stream(ReportFiled.class)).containsExactly(ReportFiled.toOperators(reportId, MAP_ID, REPORTER));
    }

    @Test
    void 프라이빗_지도는_멤버만_신고할_수_있다() {
        Place place = place(PlaceStatus.APPROVED);
        given(MapType.PRIVATE, MapMemberRole.NONE, REPORTER);

        assertErrorCode(() -> placeReportService.report(place.getId(), REPORTER, request(ReportReason.SPAM)),
            PlaceErrorCode.NOT_MAP_MEMBER);
        assertThat(placeReportRepository.findAll()).isEmpty();
    }

    @Test
    void 지도_유형을_알_수_없으면_막는다() {
        Place place = place(PlaceStatus.APPROVED);
        given(null, MapMemberRole.MEMBER, REPORTER);

        assertErrorCode(() -> placeReportService.report(place.getId(), REPORTER, request(ReportReason.SPAM)),
            PlaceErrorCode.NOT_MAP_MEMBER);
    }

    @Test
    void 내가_등록한_장소는_신고할_수_없다() {
        Place place = place(PlaceStatus.APPROVED);
        given(MapType.COMMUNITY, MapMemberRole.OWNER, CREATOR);

        assertErrorCode(() -> placeReportService.report(place.getId(), CREATOR, request(ReportReason.SPAM)),
            PlaceErrorCode.CANNOT_REPORT_OWN_PLACE);
    }

    @Test
    void 같은_장소를_두_번_신고할_수_없다() {
        Place place = place(PlaceStatus.APPROVED);
        given(MapType.COMMUNITY, MapMemberRole.NONE, REPORTER);
        placeReportService.report(place.getId(), REPORTER, request(ReportReason.SPAM));

        assertErrorCode(() -> placeReportService.report(place.getId(), REPORTER, request(ReportReason.ABUSE)),
            PlaceErrorCode.ALREADY_REPORTED_PLACE);
        assertThat(placeReportRepository.findAll()).hasSize(1);
    }

    @Test
    void 승인되지_않았거나_삭제된_장소는_없는_것으로_다룬다() {
        Place pending = place(PlaceStatus.PENDING);
        Place deleted = place(PlaceStatus.APPROVED);
        deleted.delete(CREATOR);
        placeRepository.flush();

        assertErrorCode(() -> placeReportService.report(pending.getId(), REPORTER, request(ReportReason.SPAM)),
            PlaceErrorCode.PLACE_NOT_FOUND);
        assertErrorCode(() -> placeReportService.report(deleted.getId(), REPORTER, request(ReportReason.SPAM)),
            PlaceErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    void 로그인하지_않으면_신고할_수_없다() {
        assertErrorCode(() -> placeReportService.report(1L, null, request(ReportReason.SPAM)), CommonErrorCode.UNAUTHORIZED);
    }

    private Place place(PlaceStatus status) {
        return placeRepository.saveAndFlush(Place.builder()
            .name("성수 카페")
            .description("메모")
            .lat(BigDecimal.valueOf(37.5))
            .lng(BigDecimal.valueOf(127.0))
            .kakaoPlaceId("K" + (++kakaoIdSeq))
            .sourceType(PlaceSourceType.KAKAO_SEARCH)
            .mapId(MAP_ID)
            .createdBy(CREATOR)
            .status(status)
            .build());
    }

    private void given(MapType mapType, MapMemberRole role, Long userId) {
        BDDMockito.given(mapClient.getMemberInfo(MAP_ID, userId)).willReturn(new MapMemberResponse(mapType, role, true));
    }

    private static ReportRequest request(ReportReason reason) {
        return new ReportRequest(reason, null);
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }
}
