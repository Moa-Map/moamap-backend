package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.moamap.place.ai.GeminiPlaceExtractor;
import com.moamap.place.ai.dto.ExtractedPlace;
import com.moamap.place.dto.InstagramExtractRequest;
import com.moamap.place.dto.PlaceCandidateResponse;
import com.moamap.place.kakao.KakaoLocalClient;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 인스타그램 설명글 → Gemini 장소 추출 → 카카오맵 검색 파이프라인 오케스트레이션.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceExtractionService {

    private final GeminiPlaceExtractor geminiPlaceExtractor;
    private final KakaoLocalClient kakaoLocalClient;

    public List<PlaceCandidateResponse> extractFromInstagram(InstagramExtractRequest request) {
        List<ExtractedPlace> extractedPlaces = geminiPlaceExtractor.extract(request.description());
        log.info("Gemini 추출 장소 수={}, 목록={}", extractedPlaces.size(), extractedPlaces);
        return searchAndMerge(extractedPlaces, request.url());
    }

    /**
     * 추출된 각 장소를 카카오맵에서 검색하고, kakaoPlaceId 기준으로 중복을 제거해 통합한다.
     */
    private List<PlaceCandidateResponse> searchAndMerge(List<ExtractedPlace> extractedPlaces, String sourceUrl) {
        Map<String, PlaceCandidateResponse> byId = new LinkedHashMap<>();
        List<PlaceCandidateResponse> noId = new ArrayList<>();

        for (ExtractedPlace place : extractedPlaces) {
            String keyword = place.toSearchKeyword();
            if (keyword.isBlank()) {
                log.warn("검색 키워드가 비어 있어 건너뜀: {}", place);
                continue;
            }
            List<KakaoPlaceDocument> documents;
            try {
                documents = kakaoLocalClient.searchByKeyword(keyword);
            } catch (RuntimeException e) {
                log.warn("카카오 검색 실패, 해당 장소는 건너뜀: {}", keyword, e);
                continue;
            }
            for (KakaoPlaceDocument document : documents) {
                PlaceCandidateResponse candidate = toCandidate(document, sourceUrl);
                if (candidate.kakaoPlaceId() == null) {
                    noId.add(candidate);
                } else {
                    byId.putIfAbsent(candidate.kakaoPlaceId(), candidate);
                }
            }
        }

        List<PlaceCandidateResponse> merged = new ArrayList<>(byId.values());
        merged.addAll(noId);
        return merged;
    }

    private PlaceCandidateResponse toCandidate(KakaoPlaceDocument document, String sourceUrl) {
        return new PlaceCandidateResponse(
            document.id(),
            document.placeName(),
            document.categoryName(),
            document.addressName(),
            document.roadAddressName(),
            parseDecimal(document.y()),
            parseDecimal(document.x()),
            document.placeUrl(),
            sourceUrl
        );
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
