package com.moamap.place.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.MapShareExtractRequest;
import com.moamap.place.dto.MapShareExtractResponse;
import com.moamap.place.dto.MapSharePlaceCandidate;
import com.moamap.place.dto.UnmatchReason;
import com.moamap.place.dto.UnmatchedPlaceResponse;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import com.moamap.place.mapshare.KakaoPlaceMatcher;
import com.moamap.place.mapshare.MapShareProvider;
import com.moamap.place.mapshare.ShareLinkUrlParser;
import com.moamap.place.mapshare.dto.ExtractedList;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import com.moamap.place.mapshare.dto.MatchResult;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지도 공유 링크 → 장소 추출 → 카카오 재매칭 파이프라인.
 */
@Slf4j
@Service
public class MapSharePlaceExtractionService {

    /** 한 번에 처리하는 장소 수 상한. 초과분은 잘라내고 truncated로 알린다. */
    private static final int MAX_PLACES = 100;

    /** 카카오 재매칭 동시 실행 수. 한 요청이 카카오 API 쿼터를 다 태우지 않게 묶는다. */
    private static final int MATCH_PARALLELISM = 8;

    private static final String KAKAO_PLACE_URL = "https://place.map.kakao.com/";

    /** Place 엔티티의 lat/lng 컬럼이 numeric(9,6)이라 저장 시 반올림된다. 여기서 미리 맞춰 응답과 저장값을 일치시킨다. */
    private static final int COORDINATE_SCALE = 6;

    private final List<MapShareProvider> providers;
    private final KakaoPlaceMatcher kakaoPlaceMatcher;
    private final ExecutorService matchExecutor =
        Executors.newFixedThreadPool(MATCH_PARALLELISM, runnable -> {
            Thread thread = new Thread(runnable, "map-share-match");
            thread.setDaemon(true);
            return thread;
        });

    public MapSharePlaceExtractionService(List<MapShareProvider> providers, KakaoPlaceMatcher kakaoPlaceMatcher) {
        this.providers = providers;
        this.kakaoPlaceMatcher = kakaoPlaceMatcher;
    }

    @PreDestroy
    void shutdown() {
        matchExecutor.shutdown();
    }

    public MapShareExtractResponse extract(MapShareExtractRequest request) {
        String url = ShareLinkUrlParser.firstUrl(request.url());
        if (url == null) {
            throw new BusinessException(PlaceErrorCode.UNSUPPORTED_SHARE_LINK);
        }
        MapShareProvider provider = providers.stream()
            .filter(candidate -> candidate.supports(url))
            .findFirst()
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.UNSUPPORTED_SHARE_LINK));

        String listId = provider.resolveListId(url);
        ExtractedList list = provider.fetchList(listId);

        // 재추출 비율을 측정하기 위한 로그다. 같은 listId가 얼마나 반복되는지 확인한 뒤
        // 캐싱 도입 여부를 판단한다
        log.info("지도 공유 링크 추출: source={}, listId={}, declaredCount={}, parsed={}",
            list.source(), listId, list.declaredCount(), list.places().size());

        verifyStructure(list);

        boolean truncated = list.places().size() > MAX_PLACES;
        List<ExtractedMapPlace> targets =
            truncated ? list.places().subList(0, MAX_PLACES) : list.places();

        List<MapSharePlaceCandidate> matched = new ArrayList<>();
        List<UnmatchedPlaceResponse> unmatched = new ArrayList<>();

        if (list.source() == PlaceSourceType.KAKAO_MAP) {
            // 카카오 즐겨찾기의 key는 카카오 로컬 API의 id와 같은 체계라 재매칭이 필요 없다.
            // 대신 분류·도로명주소는 비어 있다(리스트 응답에 없음).
            for (ExtractedMapPlace place : targets) {
                matched.add(fromKakaoLink(place, url));
            }
        } else {
            matchInParallel(targets, list.source(), url, matched, unmatched);
        }

        return new MapShareExtractResponse(
            list.source(), url, list.listName(), list.owner(), list.declaredCount(),
            targets.size(), truncated, matched, unmatched);
    }

    /**
     * 서비스가 알려준 개수와 실제 파싱 개수를 대조한다.
     *
     * 3사 내부 API는 비공식이라 응답 구조가 바뀔 수 있고, 특히 구글은 키 없는 중첩 배열이라
     * 포맷이 바뀌면 예외 없이 빈 결과가 나온다. 조용한 빈 응답 대신 명시적 에러로 드러낸다.
     */
    private void verifyStructure(ExtractedList list) {
        if (list.places().isEmpty() && list.declaredCount() != null && list.declaredCount() > 0) {
            log.warn("지도 공유 리스트 구조 변경 의심: source={}, listId={}, declaredCount={}, parsed=0",
                list.source(), list.listId(), list.declaredCount());
            throw new BusinessException(PlaceErrorCode.SHARE_LIST_STRUCTURE_CHANGED);
        }
    }

    private void matchInParallel(List<ExtractedMapPlace> targets, PlaceSourceType source, String sourceUrl,
            List<MapSharePlaceCandidate> matched, List<UnmatchedPlaceResponse> unmatched) {
        List<Future<MatchResult>> futures = new ArrayList<>(targets.size());
        for (ExtractedMapPlace place : targets) {
            futures.add(matchExecutor.submit(() -> kakaoPlaceMatcher.match(place)));
        }
        for (int i = 0; i < targets.size(); i++) {
            ExtractedMapPlace place = targets.get(i);
            MatchResult result = resolve(futures.get(i));
            if (result.isMatched()) {
                matched.add(fromKakaoSearch(result.document(), place, sourceUrl, source));
            } else {
                unmatched.add(new UnmatchedPlaceResponse(
                    place.name(), place.address(), place.lat(), place.lng(), result.reason()));
            }
        }
    }

    private MatchResult resolve(Future<MatchResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return MatchResult.unmatched(UnmatchReason.SEARCH_FAILED);
        } catch (ExecutionException e) {
            log.warn("카카오 재매칭 작업이 실패했습니다.", e.getCause());
            return MatchResult.unmatched(UnmatchReason.SEARCH_FAILED);
        }
    }

    /** 재매칭된 장소는 카카오 쪽 값(이름·주소·좌표·분류)을 쓴다. */
    private MapSharePlaceCandidate fromKakaoSearch(KakaoPlaceDocument document, ExtractedMapPlace place,
            String sourceUrl, PlaceSourceType source) {
        return new MapSharePlaceCandidate(
            document.id(),
            document.placeName(),
            document.categoryName(),
            document.addressName(),
            document.roadAddressName(),
            scale(parseDecimal(document.y())),
            scale(parseDecimal(document.x())),
            document.placeUrl(),
            place.memo(),
            sourceUrl,
            source);
    }

    private MapSharePlaceCandidate fromKakaoLink(ExtractedMapPlace place, String sourceUrl) {
        return new MapSharePlaceCandidate(
            place.placeId(),
            place.name(),
            place.category(),
            place.address(),
            null,
            scale(place.lat()),
            scale(place.lng()),
            place.placeId() == null ? null : KAKAO_PLACE_URL + place.placeId(),
            place.memo(),
            sourceUrl,
            PlaceSourceType.KAKAO_MAP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
