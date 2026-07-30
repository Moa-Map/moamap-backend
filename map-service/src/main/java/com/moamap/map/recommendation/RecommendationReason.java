package com.moamap.map.recommendation;

import java.util.List;

/**
 * 추천 이유 문구를 만든다.
 *
 * 지도 이름 대신 태그만 쓰는 이유: 관심사는 프라이빗 지도에서도 나오는데,
 * "비공개 지도 OO과 비슷해요"처럼 표시하면 화면을 옆에서 보는 사람에게 비공개 정보가 새어나간다.
 */
final class RecommendationReason {

    private static final int MAX_TAGS = 2;
    private static final String NO_MATCH = "지금 많이 찾는 지도예요";

    private RecommendationReason() {
    }

    static String of(List<String> matchedTags) {
        if (matchedTags == null || matchedTags.isEmpty()) {
            return NO_MATCH;
        }
        String tags = matchedTags.stream()
            .limit(MAX_TAGS)
            .map(tag -> "#" + tag)
            .reduce((a, b) -> a + " " + b)
            .orElse("");
        return "관심 태그 " + tags + "와 비슷해요";
    }
}
