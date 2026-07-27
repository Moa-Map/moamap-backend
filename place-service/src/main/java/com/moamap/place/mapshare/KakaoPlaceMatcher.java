package com.moamap.place.mapshare;

import java.math.BigDecimal;
import java.util.List;
import com.moamap.place.dto.UnmatchReason;
import com.moamap.place.kakao.KakaoLocalClient;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import com.moamap.place.mapshare.dto.MatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 추출된 장소를 카카오 장소와 재매칭해 kakaoPlaceId를 확보한다.
 *
 * 이름과 거리를 모두 본다. 두 조건은 서로 다른 오류를 거른다.
 *  - 이름 체크: 애초에 다른 가게를 거른다. 카카오 키워드 검색은 연관도 검색이라
 *    "청년다방"으로 검색해도 근처 다른 분식집이 딸려 나온다.
 *  - 거리 체크: 같은 이름의 다른 지점을 거른다(부산 청년다방, 대전 청년다방).
 *
 * 하나만으로는 부족하다. 이름만 보면 전국 스타벅스 중 어느 지점인지 모르고,
 * 거리만 보면 옆 가게를 집어 사용자 지도에 조용히 등록된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoPlaceMatcher {

    /** 채택 임계 거리(m). 3사 좌표는 같은 건물을 가리키면 보통 수십 m 이내로 일치한다. */
    private static final double MAX_DISTANCE_METERS = 100.0;

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /** 이름 비교 전에 지우는 문자: 공백, 가운데점, 하이픈, 각종 괄호. */
    private static final String NAME_NOISE = "[\\s·・\\-()\\[\\]（）]";

    private final KakaoLocalClient kakaoLocalClient;

    public MatchResult match(ExtractedMapPlace place) {
        if (place.name() == null || place.lat() == null || place.lng() == null) {
            return MatchResult.unmatched(UnmatchReason.NO_RESULT);
        }

        List<KakaoPlaceDocument> documents;
        try {
            documents = kakaoLocalClient.searchNearby(place.name(), place.lng(), place.lat());
        } catch (RuntimeException e) {
            log.warn("카카오 재매칭 검색 실패: name={}", place.name(), e);
            return MatchResult.unmatched(UnmatchReason.SEARCH_FAILED);
        }
        if (documents.isEmpty()) {
            return MatchResult.unmatched(UnmatchReason.NO_RESULT);
        }

        KakaoPlaceDocument nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        boolean anyNameMatched = false;

        for (KakaoPlaceDocument document : documents) {
            if (!nameMatches(place.name(), document.placeName())) {
                continue;
            }
            anyNameMatched = true;
            Double distance = distanceMeters(place.lat(), place.lng(), document.y(), document.x());
            if (distance == null || distance > MAX_DISTANCE_METERS) {
                continue;
            }
            if (distance < nearestDistance) {
                nearest = document;
                nearestDistance = distance;
            }
        }

        if (nearest != null) {
            return MatchResult.matched(nearest);
        }
        return MatchResult.unmatched(anyNameMatched ? UnmatchReason.TOO_FAR : UnmatchReason.NAME_MISMATCH);
    }

    /**
     * 정규화 후 완전일치 또는 한쪽이 다른 쪽을 포함하면 같은 장소로 본다.
     * 포함까지 허용하는 이유는 지점명 표기가 서비스마다 다르기 때문이다
     * ("청년다방" vs "청년다방 서울숭실대점").
     */
    static boolean nameMatches(String extractedName, String kakaoName) {
        String a = normalize(extractedName);
        String b = normalize(kakaoName);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.contains(b) || b.contains(a);
    }

    static String normalize(String name) {
        return name == null ? "" : name.toLowerCase().replaceAll(NAME_NOISE, "");
    }

    /** 하버사인 거리(m). 카카오 좌표는 문자열이라 파싱 실패 시 null. */
    static Double distanceMeters(BigDecimal lat1, BigDecimal lng1, String lat2, String lng2) {
        double la2;
        double ln2;
        try {
            la2 = Double.parseDouble(lat2);
            ln2 = Double.parseDouble(lng2);
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
        double la1 = lat1.doubleValue();
        double ln1 = lng1.doubleValue();

        double dLat = Math.toRadians(la2 - la1);
        double dLng = Math.toRadians(ln2 - ln1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
