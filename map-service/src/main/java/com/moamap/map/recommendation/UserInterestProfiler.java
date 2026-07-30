package com.moamap.map.recommendation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 참여 중인 지도들로부터 사용자의 관심 태그 가중치를 계산한다.
 *
 * 지도 한 건의 무게 = 역할 가중 x 최근성 가중.
 * - 직접 만들거나 운영하는 지도(OWNER/ADMIN)는 단순 참여보다 관심이 크다고 본다.
 * - 오래 전에 참여한 지도는 영향력을 서서히 줄여 최근 관심사가 더 드러나게 한다.
 *
 * DB나 스프링 컨텍스트에 의존하지 않는 순수 계산이라 단위 테스트로 검증한다.
 */
@Component
@RequiredArgsConstructor
public class UserInterestProfiler {

    private final RecommendationProperties properties;

    public InterestProfile profile(List<JoinedMap> joinedMaps, LocalDateTime now) {
        if (joinedMaps == null || joinedMaps.isEmpty()) {
            return InterestProfile.empty();
        }

        Map<String, Double> weights = new HashMap<>();
        for (JoinedMap joined : joinedMaps) {
            double mapWeight = signalWeight(joined.type(), joined.role()) * recencyWeight(joined.joinedAt(), now);
            // 같은 지도 안에서 태그가 중복돼도 한 번만 반영한다.
            for (String tag : distinctTags(joined.tags())) {
                weights.merge(tag, mapWeight, Double::sum);
            }
        }
        return new InterestProfile(weights);
    }

    private Set<String> distinctTags(List<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        return tags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    private double signalWeight(MapType type, MapRole role) {
        if (role == MapRole.OWNER || role == MapRole.ADMIN) {
            return properties.profile().managerRoleWeight();
        }
        // 초대로 들어간 프라이빗 지도는 "내가 고른 컨셉"이 아니므로 신호를 약하게 본다.
        if (type == MapType.PRIVATE) {
            return properties.profile().invitedMemberWeight();
        }
        return 1.0;
    }

    /**
     * 참여한 지 오래될수록 0에 가까워지는 지수 감쇠. 반감 기준일에서 약 0.37이 된다.
     */
    private double recencyWeight(LocalDateTime joinedAt, LocalDateTime now) {
        if (joinedAt == null || now == null) {
            return 1.0;
        }
        double days = Math.max(0, Duration.between(joinedAt, now).toDays());
        return Math.exp(-days / properties.profile().recencyHalfLifeDays());
    }
}
