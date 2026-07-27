package com.moamap.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PageResponse;
import com.moamap.place.dto.PlaceActivityResponse;
import com.moamap.place.entity.PlaceActivityType;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.service.PlaceActivityService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 라우팅/요청 바인딩/상태 코드만 검증한다. 권한 분기·이벤트 필터링 같은 비즈니스 로직은
 * PlaceActivityServiceTest에서 이미 검증한다.
 */
@WebMvcTest(PlaceActivityController.class)
class PlaceActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceActivityService placeActivityService;

    private PlaceActivityResponse placeAddedResponse() {
        // 델타: actorId 뒤에 actorNickname, actorProfileImageUrl 2개 필드가 추가됐다 (06_architect_blueprint_nickname.md 8장).
        return new PlaceActivityResponse(
            PlaceActivityType.PLACE_ADDED, LocalDateTime.of(2026, 7, 27, 9, 0), 1L, "민서", "http://img/1",
            31L, "연남동 카페", null, null
        );
    }

    private PageResponse<PlaceActivityResponse> page() {
        return new PageResponse<>(List.of(placeAddedResponse()), 0, 20, 1, 1, true);
    }

    @Test
    void getActivities는_성공하면_200과_페이지_결과를_반환한다() throws Exception {
        // given
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any())).willReturn(page());

        // when & then
        mockMvc.perform(get("/api/v1/places/activities").param("mapId", "10").header("X-User-Id", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].type").value("PLACE_ADDED"))
            .andExpect(jsonPath("$.data.content[0].placeId").value(31))
            .andExpect(jsonPath("$.data.content[0].placeName").value("연남동 카페"))
            .andExpect(jsonPath("$.data.content[0].reviewId").doesNotExist())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            // 델타: actorNickname/actorProfileImageUrl이 응답 JSON에 노출돼야 한다
            .andExpect(jsonPath("$.data.content[0].actorId").value(1))
            .andExpect(jsonPath("$.data.content[0].actorNickname").value("민서"))
            .andExpect(jsonPath("$.data.content[0].actorProfileImageUrl").value("http://img/1"));
    }

    @Test
    void X_User_Id_헤더가_없으면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/places/activities").param("mapId", "10"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_005"));
    }

    @Test
    void mapId가_없으면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/places/activities").header("X-User-Id", 1L))
            .andExpect(status().isBadRequest());
    }

    @Test
    void mapId가_숫자가_아니면_500이_아니라_400을_반환한다() throws Exception {
        // given: GlobalExceptionHandler에 MethodArgumentTypeMismatchException 핸들러가 추가됐다
        // (06_architect_blueprint_nickname.md 2-1장, place-service는 user-service와 동일한 패턴을 이미 적용).
        // 핸들러가 없으면 catch-all Exception 핸들러로 떨어져 500이 나간다.

        // when & then
        mockMvc.perform(get("/api/v1/places/activities").param("mapId", "abc").header("X-User-Id", 1L))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 서비스가_OFFICIAL_MAP_NO_ACTIVITY_LOG를_던지면_403과_해당_코드를_반환한다() throws Exception {
        // given
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any()))
            .willThrow(new BusinessException(PlaceErrorCode.OFFICIAL_MAP_NO_ACTIVITY_LOG));

        // when & then
        mockMvc.perform(get("/api/v1/places/activities").param("mapId", "10").header("X-User-Id", 1L))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PLACE_013"));
    }

    @Test
    void 서비스가_NOT_MAP_MEMBER를_던지면_403과_해당_코드를_반환한다() throws Exception {
        // given
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any()))
            .willThrow(new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER));

        // when & then
        mockMvc.perform(get("/api/v1/places/activities").param("mapId", "10").header("X-User-Id", 1L))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PLACE_002"));
    }

    @Test
    void 서비스가_MAP_NOT_FOUND를_던지면_404를_반환한다() throws Exception {
        // given
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any()))
            .willThrow(new BusinessException(PlaceErrorCode.MAP_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/places/activities").param("mapId", "10").header("X-User-Id", 1L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PLACE_007"));
    }

    @Test
    void 서비스가_MAP_SERVICE_UNAVAILABLE을_던지면_503을_반환한다() throws Exception {
        // given
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any()))
            .willThrow(new BusinessException(PlaceErrorCode.MAP_SERVICE_UNAVAILABLE));

        // when & then
        mockMvc.perform(get("/api/v1/places/activities").param("mapId", "10").header("X-User-Id", 1L))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("PLACE_008"));
    }

    @Test
    void size_파라미터를_생략하면_기본값_20이_적용된다() throws Exception {
        // given
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any())).willReturn(page());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        // when
        mockMvc.perform(get("/api/v1/places/activities").param("mapId", "10").header("X-User-Id", 1L))
            .andExpect(status().isOk());

        // then
        verify(placeActivityService).findByMapId(eq(10L), eq(1L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void sort_파라미터는_무시하고_고정된_정렬을_사용한다() throws Exception {
        // given: API 계약상 sort 파라미터는 무시하고 항상 occurredAt 내림차순 고정
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any())).willReturn(page());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        // when
        mockMvc.perform(get("/api/v1/places/activities")
                .param("mapId", "10")
                .param("sort", "occurredAt,asc")
                .header("X-User-Id", 1L))
            .andExpect(status().isOk());

        // then: 서비스로 전달되는 Pageable에 sort 파라미터의 영향이 반영되지 않아야 한다
        // (컨트롤러/설정이 sort를 무시하지 않으면 이 검증이 실패한다 - 리포지토리는 네이티브 쿼리의
        //  고정 ORDER BY를 쓰므로 Sort가 붙은 Pageable을 그대로 넘기면 오히려 위험하다)
        verify(placeActivityService).findByMapId(eq(10L), eq(1L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void size가_100을_초과하면_100으로_클램프된다() throws Exception {
        // given: 06_architect_blueprint_nickname.md 2-3장 "size 상한 100" - UserClient 벌크 API의
        // 100개 상한 안에서 항상 1회 호출로 끝내기 위한 전제 조건이다.
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any())).willReturn(page());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        // when
        mockMvc.perform(get("/api/v1/places/activities")
                .param("mapId", "10")
                .param("size", "500")
                .header("X-User-Id", 1L))
            .andExpect(status().isOk());

        // then
        verify(placeActivityService).findByMapId(eq(10L), eq(1L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void size가_100_이하면_그대로_유지된다() throws Exception {
        // given: 클램프 로직이 100 이하 값까지 건드리면 안 된다(예: 잘못 min/max를 바꿔 넣는 실수 방지)
        given(placeActivityService.findByMapId(eq(10L), eq(1L), any())).willReturn(page());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        // when
        mockMvc.perform(get("/api/v1/places/activities")
                .param("mapId", "10")
                .param("size", "50")
                .header("X-User-Id", 1L))
            .andExpect(status().isOk());

        // then
        verify(placeActivityService).findByMapId(eq(10L), eq(1L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }
}
