package com.moamap.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import com.moamap.map.client.KakaoAddressClient;
import com.moamap.map.client.KakaoAddressRateLimitException;
import com.moamap.map.client.KakaoAddressSearchException;
import com.moamap.map.client.PublicRestroomApiClient;
import com.moamap.map.client.PublicRestroomApiException;
import com.moamap.map.client.dto.KakaoAddressSearchResponse.Document;
import com.moamap.map.client.dto.PublicRestroomApiResponse;
import com.moamap.map.client.dto.PublicRestroomApiResponse.Body;
import com.moamap.map.client.dto.PublicRestroomApiResponse.Items;
import com.moamap.map.client.dto.PublicRestroomItem;
import com.moamap.map.entity.GeocodeStatus;
import com.moamap.map.entity.PublicRestroom;
import com.moamap.map.repository.PublicRestroomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 청사진 3-1 (A)(B)(C)(D), 3-3, 3-4 불변조건 5·6·9 를 커버하는 순수 Mockito 단위 테스트.
 *
 * 확정한 계약(청사진에 명시 안 됨 — 이 테스트가 계약):
 * - {@code PublicRestroomSyncService(PublicRestroomRepository, PublicRestroomApiClient,
 *   KakaoAddressClient, Clock)} — Clock을 주입받아 {@code syncStartedAt}을 테스트가 고정한다.
 * - {@code PublicRestroomRepository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus)}
 *   — 지오코딩 대기열 조회(신규 리포지토리 메서드, 이 테스트가 시그니처를 확정).
 * - 갱신은 {@code repository.save(PublicRestroom)}로 이뤄진다(기존 행 갱신 시에도 id를 유지한 새
 *   엔티티를 빌더로 만들어 save 호출 — 엔티티에 setter/도메인 변경 메서드를 추가하지 않아도 된다).
 */
@ExtendWith(MockitoExtension.class)
class PublicRestroomSyncServiceTest {

    private static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime SYNC_STARTED_AT = LocalDateTime.now(FIXED_CLOCK);

    @Mock
    private PublicRestroomRepository repository;
    @Mock
    private PublicRestroomApiClient apiClient;
    @Mock
    private KakaoAddressClient kakaoAddressClient;

    private PublicRestroomSyncService service;

    @BeforeEach
    void setUp() {
        service = new PublicRestroomSyncService(repository, apiClient, kakaoAddressClient, FIXED_CLOCK);
        // 기본값: 첫 실행(활성 0건) → 삭제 가드 자동 통과, 지오코딩 대기열 없음.
        // 각 테스트가 필요한 stub만 덮어쓴다.
    }

    // ---------- 테스트 픽스처 헬퍼 ----------

    private PublicRestroomItem item(String mngNo, String roadAddress, String lotAddress, String lastModifiedAt) {
        return item(mngNo, roadAddress, lotAddress, lastModifiedAt, "1", "1", "3", "y", "n", "");
    }

    private PublicRestroomItem item(String mngNo, String roadAddress, String lotAddress, String lastModifiedAt,
        String maleToilet, String maleUrinal, String maleDisabledToilet,
        String diaperTable, String emergencyBell, String entranceCctv) {
        return new PublicRestroomItem(
            mngNo, "테스트 화장실", "공원화장실", "공공",
            roadAddress, lotAddress,
            maleToilet, maleUrinal, maleDisabledToilet, "0", "0", "0",
            "1", "0", "0",
            "09:00~18:00", "상세", diaperTable, "", emergencyBell, "", entranceCctv,
            "생활폐기물", "관리기관", "02-000-0000", "", "202003",
            lastModifiedAt, "2026-09-11"
        );
    }

    private PublicRestroom.PublicRestroomBuilder baseEntity() {
        return PublicRestroom.builder()
            .maleToilet((short) 0)
            .maleUrinal((short) 0)
            .maleDisabledToilet((short) 0)
            .maleDisabledUrinal((short) 0)
            .maleChildToilet((short) 0)
            .maleChildUrinal((short) 0)
            .femaleToilet((short) 0)
            .femaleDisabledToilet((short) 0)
            .femaleChildToilet((short) 0)
            .diaperTable(false)
            .emergencyBell(false)
            .entranceCctv(false)
            .geocodeStatus(GeocodeStatus.PENDING)
            .lastSyncedAt(LocalDateTime.now(FIXED_CLOCK).minusDays(30));
    }

    private Body singlePage(List<PublicRestroomItem> items) {
        return new Body(new Items(items), items.size(), 1, items.size());
    }

    /** A/C/D 단계를 격리하려는 테스트가 쓰는 최소 응답: 원천 0건. */
    private void stubEmptyPage() {
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of()));
    }

    private void stubNoGeocodingQueue() {
        when(repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING))
            .thenReturn(List.of());
    }

    // ==================== (A) 동기화 upsert 분기 ====================

    @Test
    void 기존_행이_없으면_PENDING_상태로_신규_저장한다() {
        PublicRestroomItem newItem = item("MNG-001", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(newItem)));
        when(repository.findByMngNo("MNG-001")).thenReturn(Optional.empty());
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        stubNoGeocodingQueue();

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        PublicRestroom saved = captor.getValue();
        assertThat(saved.getMngNo()).isEqualTo("MNG-001");
        assertThat(saved.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getLastSyncedAt()).isEqualTo(SYNC_STARTED_AT);
    }

    @Test
    void 기존_행이_있고_수정시각이_다르고_주소가_변경되면_전체_갱신하며_PENDING으로_되돌리고_좌표를_지운다() {
        PublicRestroom existing = baseEntity()
            .id(10L)
            .mngNo("MNG-002")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .sourceModifiedAt(LocalDateTime.of(2026, 8, 1, 0, 0))
            .geocodeStatus(GeocodeStatus.OK)
            .lat(new BigDecimal("37.500000"))
            .lng(new BigDecimal("127.000000"))
            .build();
        PublicRestroomItem changedAddressItem =
            item("MNG-002", "서울특별시 강남구 테헤란로 999", "서울특별시 강남구 역삼동 999", "2026-09-01 10:00:00");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(changedAddressItem)));
        when(repository.findByMngNo("MNG-002")).thenReturn(Optional.of(existing));
        when(repository.countByDeletedAtIsNull()).thenReturn(1L);
        stubNoGeocodingQueue();

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        PublicRestroom saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getRoadAddress()).isEqualTo("서울특별시 강남구 테헤란로 999");
        assertThat(saved.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getLat()).isNull();
        assertThat(saved.getLng()).isNull();
        assertThat(saved.getLastSyncedAt()).isEqualTo(SYNC_STARTED_AT);
    }

    @Test
    void 기존_행이_있고_수정시각이_다르지만_주소가_동일하면_전체_갱신하되_geocode_status와_좌표를_유지한다() {
        PublicRestroom existing = baseEntity()
            .id(11L)
            .mngNo("MNG-003")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .sourceModifiedAt(LocalDateTime.of(2026, 8, 1, 0, 0))
            .geocodeStatus(GeocodeStatus.OK)
            .lat(new BigDecimal("37.500000"))
            .lng(new BigDecimal("127.000000"))
            .build();
        PublicRestroomItem sameAddressItem =
            item("MNG-003", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(sameAddressItem)));
        when(repository.findByMngNo("MNG-003")).thenReturn(Optional.of(existing));
        when(repository.countByDeletedAtIsNull()).thenReturn(1L);
        stubNoGeocodingQueue();

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        PublicRestroom saved = captor.getValue();
        assertThat(saved.getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
        assertThat(saved.getLat()).isEqualTo(new BigDecimal("37.500000"));
        assertThat(saved.getLng()).isEqualTo(new BigDecimal("127.000000"));
        assertThat(saved.getLastSyncedAt()).isEqualTo(SYNC_STARTED_AT);
    }

    @Test
    void 기존_행이_있고_수정시각이_같으면_last_synced_at만_갱신하고_나머지는_손대지_않는다() {
        PublicRestroom existing = baseEntity()
            .id(12L)
            .mngNo("MNG-004")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .sourceModifiedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
            .geocodeStatus(GeocodeStatus.OK)
            .lat(new BigDecimal("37.500000"))
            .lng(new BigDecimal("127.000000"))
            .build();
        PublicRestroomItem sameModifiedItem =
            item("MNG-004", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(sameModifiedItem)));
        when(repository.findByMngNo("MNG-004")).thenReturn(Optional.of(existing));
        when(repository.countByDeletedAtIsNull()).thenReturn(1L);
        stubNoGeocodingQueue();

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        PublicRestroom saved = captor.getValue();
        assertThat(saved.getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
        assertThat(saved.getLat()).isEqualTo(new BigDecimal("37.500000"));
        assertThat(saved.getLng()).isEqualTo(new BigDecimal("127.000000"));
        assertThat(saved.getRoadAddress()).isEqualTo("서울특별시 강남구 테헤란로 1");
        assertThat(saved.getLastSyncedAt()).isEqualTo(SYNC_STARTED_AT);
    }

    @Test
    void 소프트_삭제된_행이_수정시각_변경_없이_다시_수집되면_삭제를_해제한다() {
        PublicRestroom deleted = baseEntity()
            .id(13L)
            .mngNo("MNG-005")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .sourceModifiedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
            .geocodeStatus(GeocodeStatus.OK)
            .deletedAt(SYNC_STARTED_AT.minusDays(30))
            .build();
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(
            item("MNG-005", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00"))));
        when(repository.findByMngNo("MNG-005")).thenReturn(Optional.of(deleted));
        when(repository.countByDeletedAtIsNull()).thenReturn(1L);
        stubNoGeocodingQueue();

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNull();
        assertThat(captor.getValue().getLastSyncedAt()).isEqualTo(SYNC_STARTED_AT);
    }

    // ==================== (A') 운영자 숨김(hidden) ====================

    private PublicRestroom syncExistingHidden(String road, String lot, String lastModifiedAt) {
        PublicRestroom existing = baseEntity()
            .id(40L)
            .mngNo("MNG-040")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .sourceModifiedAt(LocalDateTime.of(2026, 8, 1, 0, 0))
            .geocodeStatus(GeocodeStatus.OK)
            .lat(new BigDecimal("37.500000"))
            .lng(new BigDecimal("127.000000"))
            .hidden(true)
            .build();
        when(apiClient.fetchPage(eq(1), anyInt()))
            .thenReturn(singlePage(List.of(item("MNG-040", road, lot, lastModifiedAt))));
        when(repository.findByMngNo("MNG-040")).thenReturn(Optional.of(existing));
        when(repository.countByDeletedAtIsNull()).thenReturn(1L);
        stubNoGeocodingQueue();

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 숨긴_행은_수정시각이_같으면_숨김을_유지한다() {
        PublicRestroom saved = syncExistingHidden(
            "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-08-01 00:00:00");

        assertThat(saved.isHidden()).isTrue();
    }

    @Test
    void 숨긴_행은_주소_외_필드만_바뀌면_숨김을_유지한다() {
        PublicRestroom saved = syncExistingHidden(
            "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00");

        assertThat(saved.isHidden()).isTrue();
    }

    @Test
    void 숨긴_행은_원천_주소가_바뀌면_숨김을_풀고_다시_지오코딩_대기로_돌린다() {
        PublicRestroom saved = syncExistingHidden(
            "서울특별시 강남구 테헤란로 999", "서울특별시 강남구 역삼동 999", "2026-09-01 10:00:00");

        assertThat(saved.isHidden()).isFalse();
        assertThat(saved.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
    }

    // ==================== (B) 값 정규화 ====================

    @Test
    void 원천_문자열_값을_정규화해서_저장한다() {
        PublicRestroomItem raw = item("MNG-005", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00",
            "", "abc", "3", "y", "n", "");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(raw)));
        when(repository.findByMngNo("MNG-005")).thenReturn(Optional.empty());
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        stubNoGeocodingQueue();

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        PublicRestroom saved = captor.getValue();
        assertThat(saved.getMaleToilet()).isEqualTo((short) 0); // 빈값 → 파싱실패 → 0
        assertThat(saved.getMaleUrinal()).isEqualTo((short) 0); // 파싱실패 → 0
        assertThat(saved.getMaleDisabledToilet()).isEqualTo((short) 3); // 정상 파싱
        assertThat(saved.isDiaperTable()).isTrue(); // "y" 대소문자 무시
        assertThat(saved.isEmergencyBell()).isFalse(); // "n"
        assertThat(saved.isEntranceCctv()).isFalse(); // 빈값
        assertThat(saved.getInstalledYm()).isNull(); // 빈값 → null
        assertThat(saved.getRemodeledYm()).isEqualTo("202003");
    }

    @Test
    void LAST_MDFCN_PNT_파싱에_실패하면_source_modified_at은_null이고_항상_변경으로_간주해_갱신한다() {
        PublicRestroom existing = baseEntity()
            .id(13L)
            .mngNo("MNG-006")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .sourceModifiedAt(LocalDateTime.of(2026, 8, 1, 0, 0))
            .geocodeStatus(GeocodeStatus.OK)
            .lat(new BigDecimal("37.500000"))
            .lng(new BigDecimal("127.000000"))
            .build();
        // 주소는 기존과 동일, 수정시각만 파싱 불가능한 값
        PublicRestroomItem malformedModifiedAt =
            item("MNG-006", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "invalid-date");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(malformedModifiedAt)));
        when(repository.findByMngNo("MNG-006")).thenReturn(Optional.of(existing));
        when(repository.countByDeletedAtIsNull()).thenReturn(1L);
        stubNoGeocodingQueue();

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        PublicRestroom saved = captor.getValue();
        assertThat(saved.getSourceModifiedAt()).isNull();
        // 주소는 동일하므로 geocode_status/좌표는 유지되지만, 갱신 자체는 일어난다(저장 호출됨).
        assertThat(saved.getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
        assertThat(saved.getLat()).isEqualTo(new BigDecimal("37.500000"));
    }

    // ==================== (C) 삭제 가드 ====================

    @Test
    void 페이지_호출이_실패하면_삭제_단계를_스킵한다() {
        when(apiClient.fetchPage(eq(1), anyInt())).thenThrow(new PublicRestroomApiException("실패"));
        when(repository.countByDeletedAtIsNull()).thenReturn(100L);
        stubNoGeocodingQueue();

        service.syncAll();

        verify(repository, never()).softDeleteStaleActive(any(), any());
    }

    @Test
    void 기존_활성_건수가_0이면_첫_실행이라_삭제_단계를_실행한다() {
        stubEmptyPage();
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        stubNoGeocodingQueue();

        service.syncAll();

        verify(repository).softDeleteStaleActive(eq(SYNC_STARTED_AT), any());
    }

    @Test
    void 수집건수가_기존의_90퍼센트_이상이면_삭제를_실행한다() {
        PublicRestroomItem one = item("MNG-007", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(one)));
        when(repository.findByMngNo("MNG-007")).thenReturn(Optional.empty());
        when(repository.countByDeletedAtIsNull()).thenReturn(1L); // E=1, C=1 → 100% >= 90%
        stubNoGeocodingQueue();

        service.syncAll();

        verify(repository).softDeleteStaleActive(eq(SYNC_STARTED_AT), any());
    }

    @Test
    void 수집건수가_기존의_90퍼센트_미만이면_삭제를_스킵한다() {
        PublicRestroomItem one = item("MNG-008", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(one)));
        when(repository.findByMngNo("MNG-008")).thenReturn(Optional.empty());
        when(repository.countByDeletedAtIsNull()).thenReturn(100L); // E=100, C=1 → 1% < 90%
        stubNoGeocodingQueue();

        service.syncAll();

        verify(repository, never()).softDeleteStaleActive(any(), any());
    }

    // ==================== (D) 지오코딩 ====================

    @Test
    void 도로명_주소로_지오코딩에_성공하면_x는_경도_y는_위도로_저장하고_OK로_바꾼다() {
        PublicRestroom pending = baseEntity()
            .id(20L).mngNo("MNG-010")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .geocodeStatus(GeocodeStatus.PENDING)
            .build();
        stubEmptyPage();
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        when(repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING))
            .thenReturn(List.of(pending));
        when(kakaoAddressClient.search("서울특별시 강남구 테헤란로 1"))
            .thenReturn(List.of(new Document("127.111111", "37.222222")));

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        PublicRestroom saved = captor.getValue();
        assertThat(saved.getLng()).isEqualByComparingTo("127.111111"); // x → lng
        assertThat(saved.getLat()).isEqualByComparingTo("37.222222");  // y → lat
        assertThat(saved.getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
    }

    @Test
    void 도로명_주소가_없으면_지번으로_시도해서_성공하면_OK로_바꾼다() {
        PublicRestroom pending = baseEntity()
            .id(21L).mngNo("MNG-011")
            .roadAddress("")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .geocodeStatus(GeocodeStatus.PENDING)
            .build();
        stubEmptyPage();
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        when(repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING))
            .thenReturn(List.of(pending));
        when(kakaoAddressClient.search("서울특별시 강남구 역삼동 1"))
            .thenReturn(List.of(new Document("127.0", "37.0")));

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
    }

    @Test
    void 도로명_결과가_0건이면_지번으로_시도해서_성공하면_OK로_바꾼다() {
        PublicRestroom pending = baseEntity()
            .id(22L).mngNo("MNG-012")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .geocodeStatus(GeocodeStatus.PENDING)
            .build();
        stubEmptyPage();
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        when(repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING))
            .thenReturn(List.of(pending));
        when(kakaoAddressClient.search("서울특별시 강남구 테헤란로 1")).thenReturn(List.of());
        when(kakaoAddressClient.search("서울특별시 강남구 역삼동 1"))
            .thenReturn(List.of(new Document("127.0", "37.0")));

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
    }

    @Test
    void 도로명과_지번_모두_결과가_0건이면_FAILED로_바꾸고_좌표는_건드리지_않는다() {
        PublicRestroom pending = baseEntity()
            .id(23L).mngNo("MNG-013")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .geocodeStatus(GeocodeStatus.PENDING)
            .build();
        stubEmptyPage();
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        when(repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING))
            .thenReturn(List.of(pending));
        when(kakaoAddressClient.search("서울특별시 강남구 테헤란로 1")).thenReturn(List.of());
        when(kakaoAddressClient.search("서울특별시 강남구 역삼동 1")).thenReturn(List.of());

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository).save(captor.capture());
        PublicRestroom saved = captor.getValue();
        assertThat(saved.getGeocodeStatus()).isEqualTo(GeocodeStatus.FAILED);
        assertThat(saved.getLat()).isNull();
        assertThat(saved.getLng()).isNull();
    }

    @Test
    void 카카오_좌표가_비었거나_숫자가_아니면_FAILED로_바꾸고_다음_대기행을_계속_처리한다() {
        PublicRestroom nullCoord = baseEntity().id(26L).mngNo("MNG-016")
            .roadAddress("서울특별시 강남구 테헤란로 6").geocodeStatus(GeocodeStatus.PENDING).build();
        PublicRestroom badCoord = baseEntity().id(27L).mngNo("MNG-017")
            .roadAddress("서울특별시 강남구 테헤란로 7").geocodeStatus(GeocodeStatus.PENDING).build();
        PublicRestroom ok = baseEntity().id(28L).mngNo("MNG-018")
            .roadAddress("서울특별시 강남구 테헤란로 8").geocodeStatus(GeocodeStatus.PENDING).build();
        stubEmptyPage();
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        when(repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING))
            .thenReturn(List.of(nullCoord, badCoord, ok));
        when(kakaoAddressClient.search("서울특별시 강남구 테헤란로 6")).thenReturn(List.of(new Document(null, "37.5")));
        when(kakaoAddressClient.search("서울특별시 강남구 테헤란로 7")).thenReturn(List.of(new Document("abc", "37.5")));
        when(kakaoAddressClient.search("서울특별시 강남구 테헤란로 8"))
            .thenReturn(List.of(new Document("127.0", "37.5")));

        service.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository, times(3)).save(captor.capture());
        List<PublicRestroom> saved = captor.getAllValues();
        assertThat(saved.get(0).getGeocodeStatus()).isEqualTo(GeocodeStatus.FAILED);
        assertThat(saved.get(0).getLat()).isNull();
        assertThat(saved.get(1).getGeocodeStatus()).isEqualTo(GeocodeStatus.FAILED);
        assertThat(saved.get(2).getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
    }

    @Test
    void 카카오_검색_예외가_나면_해당_행은_PENDING을_유지하고_다음_대기행을_계속_처리한다() {
        PublicRestroom first = baseEntity()
            .id(24L).mngNo("MNG-014")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .geocodeStatus(GeocodeStatus.PENDING)
            .build();
        PublicRestroom second = baseEntity()
            .id(25L).mngNo("MNG-015")
            .roadAddress("서울특별시 서초구 강남대로 2")
            .lotAddress("서울특별시 서초구 서초동 2")
            .geocodeStatus(GeocodeStatus.PENDING)
            .build();
        stubEmptyPage();
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        when(repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING))
            .thenReturn(List.of(first, second));
        when(kakaoAddressClient.search("서울특별시 강남구 테헤란로 1"))
            .thenThrow(new KakaoAddressSearchException("장애", new RuntimeException()));
        when(kakaoAddressClient.search("서울특별시 서초구 강남대로 2"))
            .thenReturn(List.of(new Document("127.0", "37.0")));

        service.syncAll();

        verify(kakaoAddressClient).search("서울특별시 서초구 강남대로 2"); // 다음 행은 계속 처리됨
        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository, times(1)).save(captor.capture()); // 실패한 첫 행은 save되지 않음
        assertThat(captor.getValue().getMngNo()).isEqualTo("MNG-015");
        assertThat(captor.getValue().getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
    }

    @Test
    void 카카오_한도초과_예외가_나면_해당_행은_PENDING을_유지하고_나머지_대기행은_지오코딩을_시도하지_않는다() {
        PublicRestroom first = baseEntity()
            .id(26L).mngNo("MNG-016")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .geocodeStatus(GeocodeStatus.PENDING)
            .build();
        PublicRestroom second = baseEntity()
            .id(27L).mngNo("MNG-017")
            .roadAddress("서울특별시 서초구 강남대로 2")
            .lotAddress("서울특별시 서초구 서초동 2")
            .geocodeStatus(GeocodeStatus.PENDING)
            .build();
        stubEmptyPage();
        when(repository.countByDeletedAtIsNull()).thenReturn(0L);
        when(repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING))
            .thenReturn(List.of(first, second));
        when(kakaoAddressClient.search("서울특별시 강남구 테헤란로 1"))
            .thenThrow(new KakaoAddressRateLimitException("한도초과", new RuntimeException()));

        service.syncAll();

        verify(kakaoAddressClient, never()).search("서울특별시 서초구 강남대로 2"); // 루프 즉시 중단
        verify(repository, never()).save(any());
    }

    // ==================== 불변조건 9: 재실행 idempotent ====================

    @Test
    void 같은_입력으로_두번_실행해도_두번째_실행은_last_synced_at만_바뀐다() {
        PublicRestroom existing = baseEntity()
            .id(30L)
            .mngNo("MNG-020")
            .roadAddress("서울특별시 강남구 테헤란로 1")
            .lotAddress("서울특별시 강남구 역삼동 1")
            .sourceModifiedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
            .geocodeStatus(GeocodeStatus.OK)
            .lat(new BigDecimal("37.500000"))
            .lng(new BigDecimal("127.000000"))
            .build();
        PublicRestroomItem sameItem =
            item("MNG-020", "서울특별시 강남구 테헤란로 1", "서울특별시 강남구 역삼동 1", "2026-09-01 10:00:00");
        when(apiClient.fetchPage(eq(1), anyInt())).thenReturn(singlePage(List.of(sameItem)));
        when(repository.findByMngNo("MNG-020")).thenReturn(Optional.of(existing));
        when(repository.countByDeletedAtIsNull()).thenReturn(1L);
        stubNoGeocodingQueue();

        Clock secondRunClock = Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC);
        PublicRestroomSyncService secondRunService =
            new PublicRestroomSyncService(repository, apiClient, kakaoAddressClient, secondRunClock);

        service.syncAll();
        secondRunService.syncAll();

        ArgumentCaptor<PublicRestroom> captor = ArgumentCaptor.forClass(PublicRestroom.class);
        verify(repository, times(2)).save(captor.capture());
        PublicRestroom firstRunSaved = captor.getAllValues().get(0);
        PublicRestroom secondRunSaved = captor.getAllValues().get(1);
        assertThat(firstRunSaved.getLastSyncedAt()).isEqualTo(SYNC_STARTED_AT);
        assertThat(secondRunSaved.getLastSyncedAt()).isEqualTo(LocalDateTime.now(secondRunClock));
        assertThat(secondRunSaved.getGeocodeStatus()).isEqualTo(firstRunSaved.getGeocodeStatus());
        assertThat(secondRunSaved.getLat()).isEqualTo(firstRunSaved.getLat());
        assertThat(secondRunSaved.getLng()).isEqualTo(firstRunSaved.getLng());
        assertThat(secondRunSaved.getRoadAddress()).isEqualTo(firstRunSaved.getRoadAddress());
    }
}
