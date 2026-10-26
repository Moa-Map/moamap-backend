package com.moamap.place.mapshare;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
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

    /** 이름 끝의 지점 표기: 공백 뒤 "…점"(을지로점·본점·직영점) 또는 "…별관". */
    private static final Pattern BRANCH_SUFFIX = Pattern.compile("\\s+\\S*(점|별관)$");

    /** 지점명을 뗀 상호명이 이보다 짧으면 근처 아무 가게나 포함 관계로 걸리므로 떼지 않는다. */
    private static final int MIN_BASE_NAME_LENGTH = 2;

    private final KakaoLocalClient kakaoLocalClient;

    /**
     * 지점명까지 붙은 이름으로는 카카오 검색 결과가 없거나 다른 지점 표기만 나오는 경우가 있어,
     * 1차에서 못 찾으면 지점명을 뗀 이름으로 한 번 더 검색한다. 검색 실패는 재시도하지 않는다.
     */
    public MatchResult match(ExtractedMapPlace place) {
        if (place.name() == null || place.lat() == null || place.lng() == null) {
            return MatchResult.unmatched(UnmatchReason.NO_RESULT);
        }

        MatchResult result = matchByQuery(place, place.name());
        if (result.isMatched() || result.reason() == UnmatchReason.SEARCH_FAILED) {
            return result;
        }
        String baseName = stripBranch(place.name());
        if (baseName.equals(place.name().strip())) {
            return result;
        }
        return matchByQuery(place, baseName);
    }

    private MatchResult matchByQuery(ExtractedMapPlace place, String query) {
        List<KakaoPlaceDocument> documents;
        try {
            documents = kakaoLocalClient.searchNearby(query, place.lng(), place.lat());
        } catch (RuntimeException e) {
            log.warn("카카오 재매칭 검색 실패: name={}", query, e);
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
     * 양쪽에 서로 다른 지점명이 붙으면 포함 관계가 깨지므로("○○ 직영점" vs "○○ 서이천직영점")
     * 지점명을 뗀 상호명끼리도 비교한다.
     */
    static boolean nameMatches(String extractedName, String kakaoName) {
        return contains(extractedName, kakaoName) || contains(stripBranch(extractedName), stripBranch(kakaoName));
    }

    private static boolean contains(String extractedName, String kakaoName) {
        String a = normalize(extractedName);
        String b = normalize(kakaoName);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.contains(b) || b.contains(a);
    }

    /** 이름 끝의 지점 표기를 뗀다. 뗄 게 없거나 남는 상호명이 너무 짧으면 원래 이름을 그대로 반환한다. */
    static String stripBranch(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.strip();
        String base = BRANCH_SUFFIX.matcher(trimmed).replaceFirst("");
        return normalize(base).length() < MIN_BASE_NAME_LENGTH ? trimmed : base;
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
