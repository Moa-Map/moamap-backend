package com.moamap.place.mapshare;

import java.math.BigDecimal;
import java.util.List;
import com.moamap.place.dto.UnmatchReason;
import com.moamap.place.kakao.KakaoLocalClient;
import com.moamap.place.kakao.KakaoLocalSearchException;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import com.moamap.place.mapshare.dto.MatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class KakaoPlaceMatcherTest {

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    @InjectMocks
    private KakaoPlaceMatcher matcher;

    /** 숭실대 정문 근처. 아래 거리 계산의 기준점이다. */
    private static final BigDecimal BASE_LAT = new BigDecimal("37.49585");
    private static final BigDecimal BASE_LNG = new BigDecimal("126.95781");

    private ExtractedMapPlace extracted(String name) {
        return new ExtractedMapPlace(name, "서울 동작구 상도로 369", null, BASE_LAT, BASE_LNG, "sid", null);
    }

    private KakaoPlaceDocument document(String id, String name, String lat, String lng) {
        return new KakaoPlaceDocument(id, name, "음식점 > 분식", "FD6", "음식점",
            "서울 동작구 상도동", "서울 동작구 상도로 369", lng, lat,
            "http://place.map.kakao.com/" + id);
    }

    @Test
    void match는_이름이_같고_100m_이내면_채택한다() {
        // given - 같은 좌표
        given(kakaoLocalClient.searchNearby(anyString(), any(), any()))
            .willReturn(List.of(document("111", "청년다방", "37.49585", "126.95781")));

        // when
        MatchResult result = matcher.match(extracted("청년다방"));

        // then
        assertThat(result.isMatched()).isTrue();
        assertThat(result.document().id()).isEqualTo("111");
    }

    @Test
    void match는_이름이_포함관계면_채택한다() {
        // given - 지점명 표기가 서비스마다 다르다
        given(kakaoLocalClient.searchNearby(anyString(), any(), any()))
            .willReturn(List.of(document("111", "청년다방 서울숭실대점", "37.49585", "126.95781")));

        MatchResult result = matcher.match(extracted("청년다방"));

        assertThat(result.isMatched()).isTrue();
    }

    @Test
    void match는_공백과_특수문자를_무시하고_이름을_비교한다() {
        given(kakaoLocalClient.searchNearby(anyString(), any(), any()))
            .willReturn(List.of(document("111", "스타 벅스(상도점)", "37.49585", "126.95781")));

        MatchResult result = matcher.match(extracted("스타벅스 상도점"));

        assertThat(result.isMatched()).isTrue();
    }

    @Test
    void match는_100m_이내라도_이름이_다르면_채택하지_않는다() {
        // given - 이 테스트가 이 기능의 핵심 방어선이다.
        // 카카오 키워드 검색은 연관도 검색이라 이름이 전혀 다른 가게가 섞여 나오고,
        // 거리만 보면 엉뚱한 가게가 사용자 지도에 조용히 등록된다.
        given(kakaoLocalClient.searchNearby(anyString(), any(), any()))
            .willReturn(List.of(document("999", "신전떡볶이 상도점", "37.49585", "126.95781")));

        MatchResult result = matcher.match(extracted("청년다방"));

        assertThat(result.isMatched()).isFalse();
        assertThat(result.reason()).isEqualTo(UnmatchReason.NAME_MISMATCH);
    }

    @Test
    void match는_이름이_같아도_100m를_넘으면_채택하지_않는다() {
        // given - 위도 0.01도는 약 1.1km다
        given(kakaoLocalClient.searchNearby(anyString(), any(), any()))
            .willReturn(List.of(document("222", "청년다방", "37.50585", "126.95781")));

        MatchResult result = matcher.match(extracted("청년다방"));

        assertThat(result.isMatched()).isFalse();
        assertThat(result.reason()).isEqualTo(UnmatchReason.TOO_FAR);
    }

    @Test
    void match는_조건을_통과한_것들_중_최근접을_고른다() {
        // given - 둘 다 이름이 맞고 100m 이내지만 두 번째가 더 가깝다
        given(kakaoLocalClient.searchNearby(anyString(), any(), any())).willReturn(List.of(
            document("먼쪽", "청년다방 A지점", "37.49640", "126.95781"),
            document("가까운쪽", "청년다방 B지점", "37.49586", "126.95781")
        ));

        MatchResult result = matcher.match(extracted("청년다방"));

        assertThat(result.isMatched()).isTrue();
        assertThat(result.document().id()).isEqualTo("가까운쪽");
    }

    @Test
    void match는_검색_결과가_없으면_NO_RESULT를_반환한다() {
        given(kakaoLocalClient.searchNearby(anyString(), any(), any())).willReturn(List.of());

        MatchResult result = matcher.match(extracted("청년다방"));

        assertThat(result.isMatched()).isFalse();
        assertThat(result.reason()).isEqualTo(UnmatchReason.NO_RESULT);
    }

    @Test
    void match는_검색이_실패하면_SEARCH_FAILED를_반환한다() {
        willThrow(new KakaoLocalSearchException("실패", new RuntimeException()))
            .given(kakaoLocalClient).searchNearby(anyString(), any(), any());

        MatchResult result = matcher.match(extracted("청년다방"));

        assertThat(result.isMatched()).isFalse();
        assertThat(result.reason()).isEqualTo(UnmatchReason.SEARCH_FAILED);
    }

    @Test
    void match는_좌표가_없으면_검색하지_않고_NO_RESULT를_반환한다() {
        ExtractedMapPlace noCoords =
            new ExtractedMapPlace("청년다방", "주소", null, null, null, "sid", null);

        MatchResult result = matcher.match(noCoords);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.reason()).isEqualTo(UnmatchReason.NO_RESULT);
    }
}
