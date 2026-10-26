package com.moamap.map.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 추천 알고리즘 설정. 가중치를 코드에 박지 않고 설정으로 빼서 재배포 없이 조정할 수 있게 한다.
 */
@ConfigurationProperties(prefix = "map.recommendation")
public record RecommendationProperties(
    @DefaultValue("5") int defaultSize,
    @DefaultValue("20") int maxSize,
    @DefaultValue("300") int candidateLimit,
    @DefaultValue Weights weights,
    @DefaultValue Diversity diversity,
    @DefaultValue Profile profile
) {

    /**
     * 점수 구성 요소별 비중. 합이 1이 되도록 유지한다.
     */
    public record Weights(
        @DefaultValue("0.55") double tag,
        @DefaultValue("0.25") double popularity,
        @DefaultValue("0.20") double freshness,
        @DefaultValue ColdStart coldStart
    ) {

        /**
         * 관심 프로필이 없을 때(콜드스타트) 쓰는 가중치.
         */
        public record ColdStart(
            @DefaultValue("0.80") double popularity,
            @DefaultValue("0.20") double freshness
        ) {
        }
    }

    /**
     * 추천 결과가 한쪽으로 쏠리지 않게 하는 보정값.
     */
    public record Diversity(
        // 같은 사람이 만든 지도가 결과를 채우지 않도록 제한한다.
        @DefaultValue("1") int maxPerOwner,
        // 이미 고른 지도와 태그가 겹칠수록 감점하는 정도.
        @DefaultValue("0.15") double tagOverlapPenalty
    ) {
    }

    /**
     * 관심 프로필을 만들 때 쓰는 가중치.
     */
    public record Profile(
        // 직접 만들거나 운영하는 지도는 단순 참여보다 관심이 크다고 본다.
        @DefaultValue("1.5") double managerRoleWeight,
        // 초대로 들어간 프라이빗 지도는 "내가 고른 컨셉"이 아니므로 신호를 약하게 본다.
        @DefaultValue("0.4") double invitedMemberWeight,
        // 오래 전 참여한 지도의 영향력이 서서히 줄어드는 반감 기준(일).
        @DefaultValue("60") double recencyHalfLifeDays,
        // 신선도 점수가 절반이 되는 기준(일).
        @DefaultValue("90") double freshnessHalfLifeDays
    ) {
    }
}
