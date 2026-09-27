package com.moamap.map.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.moamap.map.client.KakaoAddressClient;
import com.moamap.map.client.KakaoAddressRateLimitException;
import com.moamap.map.client.KakaoAddressSearchException;
import com.moamap.map.client.PublicRestroomApiClient;
import com.moamap.map.client.PublicRestroomApiException;
import com.moamap.map.client.dto.KakaoAddressSearchResponse.Document;
import com.moamap.map.client.dto.PublicRestroomApiResponse.Body;
import com.moamap.map.client.dto.PublicRestroomItem;
import com.moamap.map.entity.GeocodeStatus;
import com.moamap.map.entity.PublicRestroom;
import com.moamap.map.entity.PublicRestroom.PublicRestroomBuilder;
import com.moamap.map.repository.PublicRestroomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 청사진 3-3 동기화 배치. 행안부 공공화장실 전량을 페이지 단위로 수집해 upsert하고,
 * 삭제 가드를 거쳐 소프트 삭제한 뒤 PENDING 건을 카카오로 지오코딩한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicRestroomSyncService {

    private static final int PAGE_SIZE = 1000;
    private static final DateTimeFormatter SOURCE_MODIFIED_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PublicRestroomRepository repository;
    private final PublicRestroomApiClient apiClient;
    private final KakaoAddressClient kakaoAddressClient;
    private final Clock clock;

    public void syncAll() {
        LocalDateTime syncStartedAt = LocalDateTime.now(clock);
        long existingActiveCount = repository.countByDeletedAtIsNull();

        SyncPagesResult pagesResult = syncPages(syncStartedAt);

        runDeleteGuard(syncStartedAt, existingActiveCount, pagesResult);
        runGeocoding();
    }

    // ---------- 1~4단계: 페이지 수집 + upsert ----------

    private SyncPagesResult syncPages(LocalDateTime syncStartedAt) {
        boolean allPagesOk = true;
        long collectedCount = 0;
        int pageNo = 1;
        int totalPages = 1;

        while (pageNo <= totalPages) {
            Body body;
            try {
                body = apiClient.fetchPage(pageNo, PAGE_SIZE);
            } catch (PublicRestroomApiException e) {
                log.warn("공공화장실 API 페이지 호출 실패 pageNo={}", pageNo, e);
                allPagesOk = false;
                break;
            }

            if (pageNo == 1) {
                totalPages = calculateTotalPages(body);
            }

            List<PublicRestroomItem> items =
                body.items() == null || body.items().item() == null ? List.of() : body.items().item();
            for (PublicRestroomItem item : items) {
                collectedCount++;
                try {
                    upsertOne(item, syncStartedAt);
                } catch (Exception e) {
                    log.warn("공공화장실 행 처리 실패 mngNo={}", item.mngNo(), e);
                }
            }
            pageNo++;
        }

        return new SyncPagesResult(allPagesOk, collectedCount);
    }

    private int calculateTotalPages(Body body) {
        int actualNumOfRows = body.numOfRows() != null && body.numOfRows() > 0 ? body.numOfRows() : PAGE_SIZE;
        int totalCount = body.totalCount() == null ? 0 : body.totalCount();
        return totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / actualNumOfRows);
    }

    private record SyncPagesResult(boolean allPagesOk, long collectedCount) {
    }

    // ---------- (A) upsert 분기 ----------

    private void upsertOne(PublicRestroomItem item, LocalDateTime syncStartedAt) {
        Optional<PublicRestroom> existingOpt = repository.findByMngNo(item.mngNo());

        if (existingOpt.isEmpty()) {
            PublicRestroom toSave = mapItemToBuilder(item)
                .geocodeStatus(GeocodeStatus.PENDING)
                .lat(null)
                .lng(null)
                .deletedAt(null)
                .lastSyncedAt(syncStartedAt)
                .build();
            repository.save(toSave);
            return;
        }

        PublicRestroom existing = existingOpt.get();
        LocalDateTime newSourceModifiedAt = parseSourceModifiedAt(item.lastModifiedAt());
        // source_modified_at이 null로 파싱되면 변경 감지가 불가능하므로 항상 변경된 것으로 간주한다.
        boolean modifiedChanged =
            newSourceModifiedAt == null || !newSourceModifiedAt.equals(existing.getSourceModifiedAt());

        if (!modifiedChanged) {
            // 원천에 다시 나타났으니 이전에 소프트 삭제된 행이라도 복구한다.
            PublicRestroom toSave = copyBuilder(existing).deletedAt(null).lastSyncedAt(syncStartedAt).build();
            repository.save(toSave);
            return;
        }

        String newRoadAddress = normalizeString(item.roadAddress());
        String newLotAddress = normalizeString(item.lotAddress());
        boolean addressChanged = !Objects.equals(existing.getRoadAddress(), newRoadAddress)
            || !Objects.equals(existing.getLotAddress(), newLotAddress);

        PublicRestroomBuilder builder = mapItemToBuilder(item)
            .id(existing.getId())
            .deletedAt(null)
            .lastSyncedAt(syncStartedAt);

        // 주소가 바뀌면 숨김도 푼다 — 숨김 사유(틀린 주소)가 원천에서 고쳐졌을 수 있으므로 다시 지오코딩해 노출한다.
        if (addressChanged) {
            builder.geocodeStatus(GeocodeStatus.PENDING).lat(null).lng(null).hidden(false);
        } else {
            builder.geocodeStatus(existing.getGeocodeStatus()).lat(existing.getLat()).lng(existing.getLng())
                .hidden(existing.isHidden());
        }

        repository.save(builder.build());
    }

    /** 원천 item 필드를 엔티티 타입으로 정규화해 채운 빌더. id/geocodeStatus/lat/lng/deletedAt/lastSyncedAt은 호출자가 채운다. */
    private PublicRestroomBuilder mapItemToBuilder(PublicRestroomItem item) {
        return PublicRestroom.builder()
            .mngNo(item.mngNo())
            .name(item.name())
            .category(normalizeString(item.category()))
            .ownerType(normalizeString(item.ownerType()))
            .roadAddress(normalizeString(item.roadAddress()))
            .lotAddress(normalizeString(item.lotAddress()))
            .maleToilet(parseShortOrZero(item.maleToilet()))
            .maleUrinal(parseShortOrZero(item.maleUrinal()))
            .maleDisabledToilet(parseShortOrZero(item.maleDisabledToilet()))
            .maleDisabledUrinal(parseShortOrZero(item.maleDisabledUrinal()))
            .maleChildToilet(parseShortOrZero(item.maleChildToilet()))
            .maleChildUrinal(parseShortOrZero(item.maleChildUrinal()))
            .femaleToilet(parseShortOrZero(item.femaleToilet()))
            .femaleDisabledToilet(parseShortOrZero(item.femaleDisabledToilet()))
            .femaleChildToilet(parseShortOrZero(item.femaleChildToilet()))
            .openHours(normalizeString(item.openHours()))
            .openHoursDetail(normalizeString(item.openHoursDetail()))
            .diaperTable(parseYesNo(item.diaperTable()))
            .diaperTableLocation(normalizeString(item.diaperTableLocation()))
            .emergencyBell(parseYesNo(item.emergencyBell()))
            .emergencyBellLocation(normalizeString(item.emergencyBellLocation()))
            .entranceCctv(parseYesNo(item.entranceCctv()))
            .wasteDisposal(normalizeString(item.wasteDisposal()))
            .managerOrg(normalizeString(item.managerOrg()))
            .phone(normalizeString(item.phone()))
            .installedYm(normalizeString(item.installedYm()))
            .remodeledYm(normalizeString(item.remodeledYm()))
            .sourceModifiedAt(parseSourceModifiedAt(item.lastModifiedAt()))
            .dataRefDate(parseDataRefDate(item.dataRefDate()));
    }

    /** 기존 엔티티의 모든 필드를 그대로 옮겨 담는 빌더. 엔티티에 {@code toBuilder}를 추가하지 않기 위한 대체 수단. */
    private PublicRestroomBuilder copyBuilder(PublicRestroom r) {
        return PublicRestroom.builder()
            .id(r.getId())
            .mngNo(r.getMngNo())
            .name(r.getName())
            .category(r.getCategory())
            .ownerType(r.getOwnerType())
            .roadAddress(r.getRoadAddress())
            .lotAddress(r.getLotAddress())
            .maleToilet(r.getMaleToilet())
            .maleUrinal(r.getMaleUrinal())
            .maleDisabledToilet(r.getMaleDisabledToilet())
            .maleDisabledUrinal(r.getMaleDisabledUrinal())
            .maleChildToilet(r.getMaleChildToilet())
            .maleChildUrinal(r.getMaleChildUrinal())
            .femaleToilet(r.getFemaleToilet())
            .femaleDisabledToilet(r.getFemaleDisabledToilet())
            .femaleChildToilet(r.getFemaleChildToilet())
            .openHours(r.getOpenHours())
            .openHoursDetail(r.getOpenHoursDetail())
            .diaperTable(r.isDiaperTable())
            .diaperTableLocation(r.getDiaperTableLocation())
            .emergencyBell(r.isEmergencyBell())
            .emergencyBellLocation(r.getEmergencyBellLocation())
            .entranceCctv(r.isEntranceCctv())
            .wasteDisposal(r.getWasteDisposal())
            .managerOrg(r.getManagerOrg())
            .phone(r.getPhone())
            .installedYm(r.getInstalledYm())
            .remodeledYm(r.getRemodeledYm())
            .sourceModifiedAt(r.getSourceModifiedAt())
            .dataRefDate(r.getDataRefDate())
            .lat(r.getLat())
            .lng(r.getLng())
            .geocodeStatus(r.getGeocodeStatus())
            .hidden(r.isHidden())
            .lastSyncedAt(r.getLastSyncedAt())
            .deletedAt(r.getDeletedAt());
    }

    // ---------- (B) 값 정규화 ----------

    private String normalizeString(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean parseYesNo(String value) {
        return value != null && "Y".equalsIgnoreCase(value.trim());
    }

    private short parseShortOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Short.parseShort(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LocalDateTime parseSourceModifiedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), SOURCE_MODIFIED_AT_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate parseDataRefDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ---------- (C) 삭제 가드 ----------

    private void runDeleteGuard(LocalDateTime syncStartedAt, long existingActiveCount, SyncPagesResult pagesResult) {
        if (!pagesResult.allPagesOk()) {
            log.warn("일부 페이지 수집 실패로 삭제 단계를 스킵합니다.");
            return;
        }
        boolean guardPassed = existingActiveCount == 0 || pagesResult.collectedCount() >= existingActiveCount * 0.9;
        if (!guardPassed) {
            log.warn("수집 건수({})가 기존 활성 건수({})의 90% 미만이라 삭제 단계를 스킵합니다.",
                pagesResult.collectedCount(), existingActiveCount);
            return;
        }
        repository.softDeleteStaleActive(syncStartedAt, LocalDateTime.now(clock));
    }

    // ---------- (D) 지오코딩 ----------

    // 5만 건 규모 첫 배치를 한 번에 메모리로 읽지 않도록 청크 단위로 순회한다(리뷰 Blocking #2).
    private void runGeocoding() {
        List<PublicRestroom> chunk =
            repository.findByGeocodeStatusAndDeletedAtIsNullOrderByIdAsc(GeocodeStatus.PENDING);

        while (!chunk.isEmpty()) {
            Long lastIdInChunk = chunk.get(chunk.size() - 1).getId();
            if (!processChunk(chunk)) {
                return; // 한도 초과로 배치 전체를 중단
            }

            if (chunk.size() < PublicRestroomRepository.GEOCODE_QUEUE_CHUNK_SIZE) {
                return; // 마지막 청크
            }
            // id 기준으로 다음 청크를 가져온다 — 이 청크 안의 행이 처리 실패로 계속 PENDING이어도
            // 같은 조건(id > lastIdInChunk)이라 다시 걸리지 않고 대기열이 앞으로 진행된다.
            chunk = repository.findByGeocodeStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                GeocodeStatus.PENDING, lastIdInChunk,
                PageRequest.of(0, PublicRestroomRepository.GEOCODE_QUEUE_CHUNK_SIZE));
        }
    }

    /** @return 한도 초과 없이 청크 처리를 끝까지 마쳤으면 true, 한도 초과로 중간에 멈췄으면 false */
    private boolean processChunk(List<PublicRestroom> chunk) {
        for (PublicRestroom pending : chunk) {
            String road = RestroomAddressNormalizer.normalizeRoadAddress(pending.getRoadAddress());
            String lot = RestroomAddressNormalizer.normalizeLotAddress(pending.getLotAddress());

            List<Document> documents;
            try {
                documents = geocode(road, lot);
            } catch (KakaoAddressRateLimitException e) {
                log.warn("카카오 주소 검색 한도 초과로 지오코딩 루프를 중단합니다.", e);
                return false;
            } catch (KakaoAddressSearchException e) {
                log.warn("카카오 주소 검색 실패 mngNo={}", pending.getMngNo(), e);
                continue;
            }

            // 좌표가 비었거나 숫자가 아닌 응답도 FAILED로 처리한다 — 예외로 두면 남은 대기열 전체가 멈춘다.
            BigDecimal lng = documents.isEmpty() ? null : parseCoordinate(documents.get(0).x());
            BigDecimal lat = documents.isEmpty() ? null : parseCoordinate(documents.get(0).y());
            if (lng == null || lat == null) {
                repository.save(copyBuilder(pending).geocodeStatus(GeocodeStatus.FAILED).build());
            } else {
                repository.save(copyBuilder(pending)
                    .lng(lng)
                    .lat(lat)
                    .geocodeStatus(GeocodeStatus.OK)
                    .build());
            }
        }
        return true;
    }

    private BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Document> geocode(String road, String lot) {
        if (road != null && !road.isBlank()) {
            List<Document> roadResult = kakaoAddressClient.search(road);
            if (!roadResult.isEmpty()) {
                return roadResult;
            }
        }
        if (lot != null && !lot.isBlank()) {
            return kakaoAddressClient.search(lot);
        }
        return List.of();
    }
}
