package com.moamap.map.recommendation;

import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;
import com.moamap.map.dto.MapRecommendationResponse;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static com.moamap.map.recommendation.RecommendationFixtures.NOW;
import static com.moamap.map.recommendation.RecommendationFixtures.map;
import static com.moamap.map.recommendation.RecommendationFixtures.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 추천 전체 흐름(프로필 → 후보 → 점수 → 다양성)이 이어지는지와 예외 상황 처리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapRecommendationServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private MapEntityRepository mapRepository;

    @Mock
    private MapMemberRepository mapMemberRepository;

    private MapRecommendationService service() {
        RecommendationProperties props = properties();
        return new MapRecommendationService(
            mapRepository,
            mapMemberRepository,
            new UserInterestProfiler(props),
            new MapScorer(props),
            new DiversityRanker(props),
            props
        );
    }

    @Test
    void 참여한_지도의_태그와_비슷한_지도를_추천한다() {
        MapEntity joined = map(10L, 999L, List.of("맛집"), 5, NOW);
        MapEntity similar = map(20L, 200L, List.of("맛집"), 50, NOW);
        MapEntity unrelated = map(21L, 201L, List.of("캠핑"), 50, NOW);

        givenMembership(joined, MapRole.MEMBER);
        given(mapRepository.findCandidateIdsByTags(any(), anyCollection(), any(Pageable.class)))
            .willReturn(List.of(20L));
        given(mapRepository.findPopularIds(any(), any(Pageable.class))).willReturn(List.of(20L, 21L));
        given(mapRepository.findAllWithTagsByIdIn(anyCollection()))
            .willReturn(List.of(joined))
            .willReturn(List.of(similar, unrelated));

        List<MapRecommendationResponse> result = service().recommend(USER_ID, 2);

        assertThat(result.get(0).id()).isEqualTo(20L);
        assertThat(result.get(0).reason()).contains("맛집");
    }

    @Test
    void 이미_참여한_지도는_추천하지_않는다() {
        MapEntity joined = map(10L, 999L, List.of("맛집"), 5, NOW);
        MapEntity other = map(20L, 200L, List.of("맛집"), 50, NOW);

        givenMembership(joined, MapRole.MEMBER);
        given(mapRepository.findCandidateIdsByTags(any(), anyCollection(), any(Pageable.class)))
            .willReturn(List.of(10L, 20L));
        given(mapRepository.findPopularIds(any(), any(Pageable.class))).willReturn(List.of(10L, 20L));
        given(mapRepository.findAllWithTagsByIdIn(anyCollection()))
            .willReturn(List.of(joined))
            .willReturn(List.of(other));

        List<MapRecommendationResponse> result = service().recommend(USER_ID, 5);

        assertThat(result).extracting(MapRecommendationResponse::id).containsExactly(20L);
    }

    @Test
    void 내가_만든_지도는_추천하지_않는다() {
        MapEntity mine = map(30L, USER_ID, List.of("맛집"), 100, NOW);
        MapEntity others = map(31L, 500L, List.of("맛집"), 10, NOW);

        given(mapMemberRepository.findByUserId(USER_ID)).willReturn(List.of());
        given(mapRepository.findPopularIds(any(), any(Pageable.class))).willReturn(List.of(30L, 31L));
        given(mapRepository.findAllWithTagsByIdIn(anyCollection())).willReturn(List.of(mine, others));

        List<MapRecommendationResponse> result = service().recommend(USER_ID, 5);

        assertThat(result).extracting(MapRecommendationResponse::id).containsExactly(31L);
    }

    @Test
    void 참여_이력이_없으면_인기_지도로_채운다() {
        MapEntity popular = map(40L, 400L, List.of("맛집"), 500, NOW);
        MapEntity quiet = map(41L, 401L, List.of("캠핑"), 3, NOW);

        given(mapMemberRepository.findByUserId(USER_ID)).willReturn(List.of());
        given(mapRepository.findPopularIds(any(), any(Pageable.class))).willReturn(List.of(40L, 41L));
        given(mapRepository.findAllWithTagsByIdIn(anyCollection())).willReturn(List.of(popular, quiet));

        List<MapRecommendationResponse> result = service().recommend(USER_ID, 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(40L);
        assertThat(result.get(0).reason()).isEqualTo("지금 많이 찾는 지도예요");
    }

    @Test
    void 비로그인이면_멤버십을_조회하지_않고_인기_지도를_돌려준다() {
        MapEntity popular = map(50L, 500L, List.of("맛집"), 100, NOW);

        given(mapRepository.findPopularIds(any(), any(Pageable.class))).willReturn(List.of(50L));
        given(mapRepository.findAllWithTagsByIdIn(anyCollection())).willReturn(List.of(popular));

        List<MapRecommendationResponse> result = service().recommend(null, 5);

        assertThat(result).extracting(MapRecommendationResponse::id).containsExactly(50L);
        verify(mapMemberRepository, never()).findByUserId(anyLong());
    }

    @Test
    void 추천할_지도가_없으면_빈_목록을_돌려준다() {
        given(mapMemberRepository.findByUserId(USER_ID)).willReturn(List.of());
        given(mapRepository.findPopularIds(any(), any(Pageable.class))).willReturn(List.of());

        assertThat(service().recommend(USER_ID, 5)).isEmpty();
    }

    @Test
    void size를_지정하지_않으면_기본값만큼_돌려준다() {
        given(mapMemberRepository.findByUserId(USER_ID)).willReturn(List.of());
        given(mapRepository.findPopularIds(any(), any(Pageable.class)))
            .willReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        given(mapRepository.findAllWithTagsByIdIn(anyCollection())).willReturn(List.of(
            map(1L, 101L, List.of("a"), 70, NOW),
            map(2L, 102L, List.of("b"), 60, NOW),
            map(3L, 103L, List.of("c"), 50, NOW),
            map(4L, 104L, List.of("d"), 40, NOW),
            map(5L, 105L, List.of("e"), 30, NOW),
            map(6L, 106L, List.of("f"), 20, NOW),
            map(7L, 107L, List.of("g"), 10, NOW)
        ));

        assertThat(service().recommend(USER_ID, null)).hasSize(5);
    }

    @Test
    void size가_상한을_넘으면_상한까지만_돌려준다() {
        given(mapMemberRepository.findByUserId(USER_ID)).willReturn(List.of());
        given(mapRepository.findPopularIds(any(), any(Pageable.class))).willReturn(List.of(1L));
        given(mapRepository.findAllWithTagsByIdIn(anyCollection()))
            .willReturn(List.of(map(1L, 101L, List.of("a"), 10, NOW)));

        // 상한(20)을 넘겨 요청해도 예외 없이 동작한다.
        assertThat(service().recommend(USER_ID, 999)).hasSize(1);
    }

    @Test
    void 태그_후보가_상한을_채우면_인기_지도_조회를_생략한다() {
        RecommendationProperties props = properties();
        MapEntity joinedMap = map(9000L, 9000L, List.of("맛집"), 5, NOW);
        List<Long> tagIds = LongStream.rangeClosed(1, props.candidateLimit()).boxed().toList();

        givenMembership(joinedMap, MapRole.MEMBER);
        given(mapRepository.findCandidateIdsByTags(any(), anyCollection(), any(Pageable.class)))
            .willReturn(tagIds);
        given(mapRepository.findAllWithTagsByIdIn(anyCollection()))
            .willReturn(List.of(joinedMap))
            .willReturn(List.of());

        service().recommend(USER_ID, 5);

        verify(mapRepository, never()).findPopularIds(any(), any(Pageable.class));
    }

    @Test
    void 태그_후보가_일부만_차면_남은_자리만큼만_인기_지도를_요청한다() {
        RecommendationProperties props = properties();
        int tagCount = 50;
        MapEntity joinedMap = map(9000L, 9000L, List.of("맛집"), 5, NOW);
        List<Long> tagIds = LongStream.rangeClosed(1, tagCount).boxed().toList();

        givenMembership(joinedMap, MapRole.MEMBER);
        given(mapRepository.findCandidateIdsByTags(any(), anyCollection(), any(Pageable.class)))
            .willReturn(tagIds);
        given(mapRepository.findPopularIds(any(), any(Pageable.class))).willReturn(List.of());
        given(mapRepository.findAllWithTagsByIdIn(anyCollection()))
            .willReturn(List.of(joinedMap))
            .willReturn(List.of());

        service().recommend(USER_ID, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mapRepository).findPopularIds(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(props.candidateLimit() - tagCount);
    }

    @Test
    void 후보_총_개수는_candidate_limit을_넘지_않는다() {
        RecommendationProperties props = properties();
        int tagCount = 250;
        MapEntity joinedMap = map(9000L, 9000L, List.of("맛집"), 5, NOW);
        List<Long> tagIds = LongStream.rangeClosed(1, tagCount).boxed().toList();
        List<Long> popularIds = LongStream.rangeClosed(1000, 1299).boxed().toList();

        givenMembership(joinedMap, MapRole.MEMBER);
        given(mapRepository.findCandidateIdsByTags(any(), anyCollection(), any(Pageable.class)))
            .willReturn(tagIds);
        // 실제 Spring Data JPA는 Pageable의 페이지 크기를 SQL LIMIT으로 내리므로,
        // 요청받은 크기만큼만 잘라 돌려주도록 스텁도 그 동작을 그대로 모사한다.
        given(mapRepository.findPopularIds(any(), any(Pageable.class)))
            .willAnswer(invocation -> {
                int pageSize = invocation.getArgument(1, Pageable.class).getPageSize();
                return popularIds.subList(0, Math.min(pageSize, popularIds.size()));
            });
        given(mapRepository.findAllWithTagsByIdIn(anyCollection()))
            .willReturn(List.of(joinedMap))
            .willReturn(List.of());

        service().recommend(USER_ID, 5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mapRepository, times(2)).findAllWithTagsByIdIn(idsCaptor.capture());
        Collection<Long> candidateIds = idsCaptor.getAllValues().get(1);

        assertThat(candidateIds.size()).isEqualTo(props.candidateLimit());
    }

    private void givenMembership(MapEntity map, MapRole role) {
        MapMember member = MapMember.of(map.getId(), USER_ID, role);
        ReflectionTestUtils.setField(member, "createdAt", NOW);
        given(mapMemberRepository.findByUserId(USER_ID)).willReturn(List.of(member));
    }
}
