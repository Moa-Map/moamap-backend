package com.moamap.map.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.moamap.common.exception.BusinessException;
import com.moamap.map.dto.PublicRestroomDetailResponse;
import com.moamap.map.dto.PublicRestroomListResponse;
import com.moamap.map.dto.RestroomBoundsRequest;
import com.moamap.map.entity.GeocodeStatus;
import com.moamap.map.entity.PublicRestroom;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.PublicRestroomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicRestroomQueryServiceTest {

    @Mock
    private PublicRestroomRepository restroomRepository;

    private PublicRestroomQueryService queryService;

    private final RestroomBoundsRequest bounds = new RestroomBoundsRequest(
        BigDecimal.valueOf(37.4), BigDecimal.valueOf(126.9), BigDecimal.valueOf(37.6), BigDecimal.valueOf(127.1));

    private PublicRestroom restroom(long id) {
        return PublicRestroom.builder()
            .id(id)
            .mngNo("MNG-" + id)
            .name("화장실" + id)
            .category("공중화장실")
            .maleToilet((short) 1)
            .maleUrinal((short) 1)
            .maleDisabledToilet((short) 0)
            .maleDisabledUrinal((short) 0)
            .maleChildToilet((short) 0)
            .maleChildUrinal((short) 0)
            .femaleToilet((short) 1)
            .femaleDisabledToilet((short) 0)
            .femaleChildToilet((short) 0)
            .lat(BigDecimal.valueOf(37.5))
            .lng(BigDecimal.valueOf(127.0))
            .geocodeStatus(GeocodeStatus.OK)
            .lastSyncedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void 목록_조회는_리포지토리_결과를_마커_응답으로_매핑한다() {
        // given
        given(restroomRepository.findMarkersInBounds(any(), any(), any(), any(), any(Pageable.class)))
            .willReturn(List.of(restroom(1L), restroom(2L)));
        queryService = new PublicRestroomQueryService(restroomRepository);

        // when
        PublicRestroomListResponse response = queryService.listMarkers(bounds);

        // then
        assertThat(response.restrooms()).hasSize(2);
        assertThat(response.restrooms().get(0).id()).isEqualTo(1L);
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void 조회_결과가_501건이면_500건만_반환하고_truncated는_true다() {
        // given
        List<PublicRestroom> rows = java.util.stream.LongStream.rangeClosed(1, 501)
            .mapToObj(this::restroom)
            .toList();
        given(restroomRepository.findMarkersInBounds(any(), any(), any(), any(), any(Pageable.class)))
            .willReturn(rows);
        queryService = new PublicRestroomQueryService(restroomRepository);

        // when
        PublicRestroomListResponse response = queryService.listMarkers(bounds);

        // then
        assertThat(response.restrooms()).hasSize(500);
        assertThat(response.truncated()).isTrue();
    }

    @Test
    void 조회_결과가_500건_이하면_truncated는_false다() {
        // given
        List<PublicRestroom> rows = java.util.stream.LongStream.rangeClosed(1, 500)
            .mapToObj(this::restroom)
            .toList();
        given(restroomRepository.findMarkersInBounds(any(), any(), any(), any(), any(Pageable.class)))
            .willReturn(rows);
        queryService = new PublicRestroomQueryService(restroomRepository);

        // when
        PublicRestroomListResponse response = queryService.listMarkers(bounds);

        // then
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void 목록_조회는_상한_501건으로_리포지토리를_호출한다() {
        // given
        given(restroomRepository.findMarkersInBounds(any(), any(), any(), any(), any(Pageable.class)))
            .willReturn(List.of());
        queryService = new PublicRestroomQueryService(restroomRepository);

        // when
        queryService.listMarkers(bounds);

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(restroomRepository).findMarkersInBounds(
            eq(bounds.swLat()), eq(bounds.swLng()), eq(bounds.neLat()), eq(bounds.neLng()), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 501));
    }

    @Test
    void 상세_조회는_존재하는_활성_행을_응답으로_반환한다() {
        // given
        given(restroomRepository.findByIdAndDeletedAtIsNullAndHiddenFalse(1L)).willReturn(java.util.Optional.of(restroom(1L)));
        queryService = new PublicRestroomQueryService(restroomRepository);

        // when
        PublicRestroomDetailResponse response = queryService.getDetail(1L);

        // then
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_id는_RESTROOM_NOT_FOUND_예외를_던진다() {
        // given
        given(restroomRepository.findByIdAndDeletedAtIsNullAndHiddenFalse(999L)).willReturn(java.util.Optional.empty());
        queryService = new PublicRestroomQueryService(restroomRepository);

        // when & then
        assertThatThrownBy(() -> queryService.getDetail(999L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(MapErrorCode.RESTROOM_NOT_FOUND));
    }

    @Test
    void 삭제된_행은_리포지토리가_이미_필터링하므로_존재하지_않는_id와_동일하게_처리된다() {
        // given: findByIdAndDeletedAtIsNullAndHiddenFalse는 삭제·숨김 행에 대해 empty를 반환한다.
        given(restroomRepository.findByIdAndDeletedAtIsNullAndHiddenFalse(5L)).willReturn(java.util.Optional.empty());
        queryService = new PublicRestroomQueryService(restroomRepository);

        // when & then
        assertThatThrownBy(() -> queryService.getDetail(5L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(MapErrorCode.RESTROOM_NOT_FOUND));
    }
}
