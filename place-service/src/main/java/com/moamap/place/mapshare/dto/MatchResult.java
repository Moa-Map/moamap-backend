package com.moamap.place.mapshare.dto;

import com.moamap.place.dto.UnmatchReason;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;

/**
 * 재매칭 결과. document와 reason 중 정확히 하나만 채워진다.
 */
public record MatchResult(KakaoPlaceDocument document, UnmatchReason reason) {

    public static MatchResult matched(KakaoPlaceDocument document) {
        return new MatchResult(document, null);
    }

    public static MatchResult unmatched(UnmatchReason reason) {
        return new MatchResult(null, reason);
    }

    public boolean isMatched() {
        return document != null;
    }
}
