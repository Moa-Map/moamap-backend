package com.moamap.map.recommendation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.moamap.map.dto.MapRecommendationResponse;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapType;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자에게 맞는 커뮤니티 지도를 추천한다.
 *
 * 흐름: 관심 프로필 만들기 → 후보 모으기 → 점수 매기기 → 다양성 보정 → 응답 조립
 *
 * 참여 이력이 없거나 비로그인이어도 인기·신선도 기준으로 채워 넣어, 화면이 비지 않도록 항상 결과를 돌려준다.
 * 요청마다 계산하지만 쿼리 3회와 후보 수백 건의 산술 연산이라 가볍고, 참여·탈퇴가 즉시 반영되는 이점이 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapRecommendationService {

    private final MapEntityRepository mapRepository;
    private final MapMemberRepository mapMemberRepository;
    private final UserInterestProfiler profiler;
    private final MapScorer scorer;
    private final DiversityRanker ranker;
    private final RecommendationProperties properties;

    public List<MapRecommendationResponse> recommend(Long userId, Integer requestedSize) {
        int size = resolveSize(requestedSize);
        LocalDateTime now = LocalDateTime.now();

        List<MapMember> memberships = (userId == null) ? List.of() : mapMemberRepository.findByUserId(userId);
        InterestProfile profile = buildProfile(memberships, now);
        Set<Long> excludedMapIds = memberships.stream()
            .map(MapMember::getMapId)
            .collect(Collectors.toSet());

        List<MapEntity> candidates = collectCandidates(profile, excludedMapIds, userId);
        List<ScoredMap> scored = scorer.score(candidates, profile, now);
        List<ScoredMap> ranked = ranker.rank(scored, size);

        return ranked.stream()
            .map(entry -> MapRecommendationResponse.of(entry.map(), RecommendationReason.of(entry.matchedTags())))
            .toList();
    }

    /**
     * 참여 중인 지도의 태그를 모아 관심 프로필을 만든다.
     * 커뮤니티·프라이빗을 모두 재료로 쓰되, 추천 결과는 커뮤니티 지도만 나가므로 비공개 정보는 노출되지 않는다.
     */
    private InterestProfile buildProfile(List<MapMember> memberships, LocalDateTime now) {
        if (memberships.isEmpty()) {
            return InterestProfile.empty();
        }
        List<Long> mapIds = memberships.stream().map(MapMember::getMapId).toList();
        Map<Long, MapEntity> joinedMaps = mapRepository.findAllWithTagsByIdIn(mapIds).stream()
            .collect(Collectors.toMap(MapEntity::getId, Function.identity()));

        List<JoinedMap> joined = memberships.stream()
            .map(membership -> {
                MapEntity map = joinedMaps.get(membership.getMapId());
                return (map == null) ? null
                    : new JoinedMap(map.getTags(), membership.getRole(), membership.getCreatedAt());
            })
            .filter(Objects::nonNull)
            .toList();

        return profiler.profile(joined, now);
    }

    /**
     * 관심 태그와 겹치는 지도를 우선 모으고, 모자라면 인기 지도로 채운다.
     * 이미 참여했거나 직접 만든 지도는 추천 대상에서 뺀다.
     */
    private List<MapEntity> collectCandidates(InterestProfile profile, Set<Long> excludedMapIds, Long userId) {
        PageRequest limit = PageRequest.of(0, properties.candidateLimit());
        Set<Long> candidateIds = new LinkedHashSet<>();

        if (!profile.isEmpty()) {
            candidateIds.addAll(
                mapRepository.findCandidateIdsByTags(MapType.COMMUNITY, profile.tags(), limit));
        }
        // 태그 후보만으로는 다양성이 부족할 수 있어 인기 지도를 함께 넣는다. 콜드스타트일 때는 이쪽이 전부다.
        candidateIds.addAll(mapRepository.findPopularIds(MapType.COMMUNITY, limit));
        candidateIds.removeAll(excludedMapIds);

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<MapEntity> candidates = new ArrayList<>(mapRepository.findAllWithTagsByIdIn(candidateIds));
        candidates.removeIf(map -> isOwnedBy(map, userId));
        return candidates;
    }

    private boolean isOwnedBy(MapEntity map, Long userId) {
        return userId != null && map.isOwnedBy(userId);
    }

    private int resolveSize(Integer requestedSize) {
        if (requestedSize == null || requestedSize <= 0) {
            return properties.defaultSize();
        }
        return Math.min(requestedSize, properties.maxSize());
    }
}
