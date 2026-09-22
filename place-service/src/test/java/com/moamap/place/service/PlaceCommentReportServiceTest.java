package com.moamap.place.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceCommentReportRequest;
import com.moamap.place.dto.PlaceCommentReportResponse;
import com.moamap.place.dto.ReportedCommentResponse;
import com.moamap.place.entity.CommentReportReason;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceComment;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceCommentReportRepository;
import com.moamap.place.repository.PlaceCommentRepository;
import com.moamap.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlaceCommentReportServiceTest {

    private static final Long PLACE_ID = 1L;
    private static final Long COMMENT_ID = 5L;
    private static final Long MAP_ID = 10L;
    private static final Long AUTHOR_ID = 2L;
    private static final Long REPORTER_ID = 3L;

    @Mock
    private PlaceCommentRepository placeCommentRepository;

    @Mock
    private PlaceCommentReportRepository placeCommentReportRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MapClient mapClient;

    @InjectMocks
    private PlaceCommentReportService placeCommentReportService;

    private Place place() {
        return Place.builder().name("스타벅스 강남점").mapId(MAP_ID).createdBy(1L).build();
    }

    private PlaceComment comment() {
        return PlaceComment.builder().placeId(PLACE_ID).userId(AUTHOR_ID).rating(3).content("광고글").build();
    }

    private PlaceCommentReportRequest request() {
        return new PlaceCommentReportRequest(CommentReportReason.SPAM, "다른 가게 홍보입니다.");
    }

    private void givenMember(MapMemberRole role) {
        given(mapClient.getMemberInfo(MAP_ID, REPORTER_ID))
            .willReturn(new MapMemberResponse(MapType.COMMUNITY, role));
    }

    @Test
    void 멤버는_남의_댓글을_신고할_수_있고_신고_수가_올라간다() {
        // given
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).willReturn(Optional.of(comment()));
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        givenMember(MapMemberRole.MEMBER);
        given(placeCommentReportRepository.existsByPlaceCommentIdAndReporterId(COMMENT_ID, REPORTER_ID))
            .willReturn(false);

        // when
        PlaceCommentReportResponse response =
            placeCommentReportService.report(PLACE_ID, COMMENT_ID, REPORTER_ID, request());

        // then
        assertThat(response.commentId()).isEqualTo(COMMENT_ID);
        assertThat(response.reason()).isEqualTo(CommentReportReason.SPAM);
        assertThat(response.reportedAt()).isNotNull();
        verify(placeCommentReportRepository).saveAndFlush(any());
        verify(placeCommentRepository).increaseReportCount(COMMENT_ID);
    }

    /** 신고자에게 누적 수를 알려주면 낙인 효과가 생긴다. 응답에 수를 담지 않는 계약을 고정한다. */
    @Test
    void 신고_접수_응답에는_누적_신고_수가_담기지_않는다() {
        // given
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).willReturn(Optional.of(comment()));
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        givenMember(MapMemberRole.MEMBER);
        given(placeCommentReportRepository.existsByPlaceCommentIdAndReporterId(COMMENT_ID, REPORTER_ID))
            .willReturn(false);

        // when
        PlaceCommentReportResponse response =
            placeCommentReportService.report(PLACE_ID, COMMENT_ID, REPORTER_ID, request());

        // then
        assertThat(response).hasNoNullFieldsOrProperties();
        assertThat(PlaceCommentReportResponse.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("reportCount");
    }

    @Test
    void 비로그인은_신고할_수_없다() {
        // when & then
        assertThatThrownBy(() -> placeCommentReportService.report(PLACE_ID, COMMENT_ID, null, request()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(placeCommentReportRepository);
    }

    @Test
    void 비멤버는_신고할_수_없다() {
        // given
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).willReturn(Optional.of(comment()));
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        givenMember(MapMemberRole.NONE);

        // when & then
        assertThatThrownBy(() -> placeCommentReportService.report(PLACE_ID, COMMENT_ID, REPORTER_ID, request()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER);
        verify(placeCommentRepository, never()).increaseReportCount(any());
    }

    @Test
    void 본인이_쓴_댓글은_신고할_수_없다() {
        // given
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).willReturn(Optional.of(comment()));
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        given(mapClient.getMemberInfo(MAP_ID, AUTHOR_ID))
            .willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));

        // when & then
        assertThatThrownBy(() -> placeCommentReportService.report(PLACE_ID, COMMENT_ID, AUTHOR_ID, request()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.CANNOT_REPORT_OWN_COMMENT);
        verify(placeCommentRepository, never()).increaseReportCount(any());
    }

    @Test
    void 같은_댓글을_두_번_신고하면_거부된다() {
        // given
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).willReturn(Optional.of(comment()));
        given(placeRepository.findByIdAndDeletedAtIsNull(PLACE_ID)).willReturn(Optional.of(place()));
        givenMember(MapMemberRole.MEMBER);
        given(placeCommentReportRepository.existsByPlaceCommentIdAndReporterId(COMMENT_ID, REPORTER_ID))
            .willReturn(true);

        // when & then
        assertThatThrownBy(() -> placeCommentReportService.report(PLACE_ID, COMMENT_ID, REPORTER_ID, request()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.ALREADY_REPORTED_COMMENT);
        verify(placeCommentReportRepository, never()).saveAndFlush(any());
        verify(placeCommentRepository, never()).increaseReportCount(any());
    }

    @Test
    void 없는_댓글은_신고할_수_없다() {
        // given
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> placeCommentReportService.report(PLACE_ID, COMMENT_ID, REPORTER_ID, request()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void 다른_장소의_댓글_id로_신고하면_404다() {
        // given: 댓글은 placeId=1에 달려 있는데 placeId=99로 들어왔다
        given(placeCommentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).willReturn(Optional.of(comment()));

        // when & then
        assertThatThrownBy(() -> placeCommentReportService.report(99L, COMMENT_ID, REPORTER_ID, request()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void 신고_목록은_지도_OWNER가_조회할_수_있다() {
        // given
        givenReportedPage();
        givenMember(MapMemberRole.OWNER);

        // when
        PageResponse<ReportedCommentResponse> response =
            placeCommentReportService.findReported(MAP_ID, REPORTER_ID, PageRequest.of(0, 20));

        // then
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).reportCount()).isEqualTo(3);
    }

    @Test
    void 신고_목록은_지도_ADMIN이_조회할_수_있다() {
        // given
        givenReportedPage();
        givenMember(MapMemberRole.ADMIN);

        // when
        PageResponse<ReportedCommentResponse> response =
            placeCommentReportService.findReported(MAP_ID, REPORTER_ID, PageRequest.of(0, 20));

        // then
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void 신고_목록은_일반_멤버가_조회할_수_없다() {
        // given
        givenMember(MapMemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() ->
            placeCommentReportService.findReported(MAP_ID, REPORTER_ID, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PlaceErrorCode.NOT_REVIEWER);
        verify(placeCommentRepository, never()).findReportedByMapId(any(), any());
    }

    @Test
    void 신고_목록은_비로그인이_조회할_수_없다() {
        // when & then
        assertThatThrownBy(() -> placeCommentReportService.findReported(MAP_ID, null, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(mapClient);
    }

    private void givenReportedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        ReportedCommentResponse row = new ReportedCommentResponse(
            COMMENT_ID, PLACE_ID, "스타벅스 강남점", AUTHOR_ID, "광고글", 3, 3, LocalDateTime.now());
        given(placeCommentRepository.findReportedByMapId(MAP_ID, pageable))
            .willReturn(new PageImpl<>(List.of(row), pageable, 1));
    }
}
