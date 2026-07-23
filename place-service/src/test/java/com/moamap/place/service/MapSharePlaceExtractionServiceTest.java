package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.MapShareExtractRequest;
import com.moamap.place.dto.MapShareExtractResponse;
import com.moamap.place.dto.MapSharePlaceCandidate;
import com.moamap.place.dto.UnmatchReason;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import com.moamap.place.mapshare.KakaoPlaceMatcher;
import com.moamap.place.mapshare.MapShareProvider;
import com.moamap.place.mapshare.dto.ExtractedList;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import com.moamap.place.mapshare.dto.MatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapSharePlaceExtractionServiceTest {

    @Mock
    private KakaoPlaceMatcher kakaoPlaceMatcher;

    private ExtractedMapPlace place(String name) {
        return new ExtractedMapPlace(name, "서울 동작구 상도로 369", "카페",
            new BigDecimal("37.49585"), new BigDecimal("126.95781"), "sid-" + name, "메모-" + name);
    }

    private KakaoPlaceDocument document(String id, String name) {
        return new KakaoPlaceDocument(id, name, "음식점 > 분식", "FD6", "음식점",
            "서울 동작구 상도동", "서울 동작구 상도로 369", "126.95781", "37.49585",
            "http://place.map.kakao.com/" + id);
    }

    /** 지정한 리스트를 그대로 돌려주는 가짜 Provider. */
    private MapShareProvider provider(PlaceSourceType source, String host, ExtractedList list) {
        return new MapShareProvider() {
            @Override
            public PlaceSourceType source() {
                return source;
            }

            @Override
            public boolean supports(String url) {
                return url.contains(host);
            }

            @Override
            public String resolveListId(String url) {
                return "list-id";
            }

            @Override
            public ExtractedList fetchList(String listId) {
                return list;
            }
        };
    }

    private MapSharePlaceExtractionService service(MapShareProvider... providers) {
        return new MapSharePlaceExtractionService(List.of(providers), kakaoPlaceMatcher);
    }

    @Test
    void extract는_매칭된_장소를_카카오_정보로_채운다() {
        // given
        ExtractedList list = new ExtractedList(PlaceSourceType.NAVER_MAP, "list-id", "원슐랭",
            null, 1, List.of(place("청년다방")));
        given(kakaoPlaceMatcher.match(any())).willReturn(MatchResult.matched(document("111", "청년다방 상도점")));

        // when
        MapShareExtractResponse response = service(provider(PlaceSourceType.NAVER_MAP, "naver.me", list))
            .extract(new MapShareExtractRequest("https://naver.me/xAbC1234"));

        // then
        assertThat(response.matched()).hasSize(1);
        MapSharePlaceCandidate candidate = response.matched().get(0);
        assertThat(candidate.kakaoPlaceId()).isEqualTo("111");
        assertThat(candidate.name()).isEqualTo("청년다방 상도점");
        assertThat(candidate.roadAddress()).isEqualTo("서울 동작구 상도로 369");
        assertThat(candidate.sourceType()).isEqualTo(PlaceSourceType.NAVER_MAP);
        assertThat(candidate.sourceUrl()).isEqualTo("https://naver.me/xAbC1234");
        // 메모는 등록 시 description으로 들어간다
        assertThat(candidate.description()).isEqualTo("메모-청년다방");
    }

    @Test
    void extract는_매칭_실패건을_사유와_함께_따로_보고한다() {
        ExtractedList list = new ExtractedList(PlaceSourceType.NAVER_MAP, "list-id", "원슐랭",
            null, 2, List.of(place("청년다방"), place("없는가게")));
        // 재매칭은 병렬로 호출되므로 호출 순서에 의존하는 스텁은 flaky하다.
        // 입력(장소명)에 따라 결과를 고정한다.
        given(kakaoPlaceMatcher.match(argThat(p -> p != null && "청년다방".equals(p.name()))))
            .willReturn(MatchResult.matched(document("111", "청년다방")));
        given(kakaoPlaceMatcher.match(argThat(p -> p != null && "없는가게".equals(p.name()))))
            .willReturn(MatchResult.unmatched(UnmatchReason.NAME_MISMATCH));

        MapShareExtractResponse response = service(provider(PlaceSourceType.NAVER_MAP, "naver.me", list))
            .extract(new MapShareExtractRequest("https://naver.me/xAbC1234"));

        assertThat(response.matched()).hasSize(1);
        assertThat(response.unmatched()).hasSize(1);
        assertThat(response.unmatched().get(0).name()).isEqualTo("없는가게");
        assertThat(response.unmatched().get(0).reason()).isEqualTo(UnmatchReason.NAME_MISMATCH);
        assertThat(response.extractedCount()).isEqualTo(2);
    }

    @Test
    void extract는_카카오_링크는_재매칭없이_key를_그대로_쓴다() {
        // given - 카카오 즐겨찾기의 key는 카카오 로컬 API의 id와 같은 체계다
        ExtractedList list = new ExtractedList(PlaceSourceType.KAKAO_MAP, "23211144", "그룹그룹그룹",
            "이중희", 1, List.of(place("숭실대학교")));

        MapShareExtractResponse response = service(provider(PlaceSourceType.KAKAO_MAP, "kko.to", list))
            .extract(new MapShareExtractRequest("https://kko.to/0FyvknIfua"));

        assertThat(response.matched()).hasSize(1);
        assertThat(response.matched().get(0).kakaoPlaceId()).isEqualTo("sid-숭실대학교");
        assertThat(response.matched().get(0).placeUrl()).isEqualTo("https://place.map.kakao.com/sid-숭실대학교");
        // 재매칭을 아예 하지 않는다
        org.mockito.Mockito.verifyNoInteractions(kakaoPlaceMatcher);
    }

    @Test
    void extract는_100개를_넘으면_잘라내고_truncated를_표시한다() {
        List<ExtractedMapPlace> many = new ArrayList<>();
        for (int i = 0; i < 106; i++) {
            many.add(place("가게" + i));
        }
        ExtractedList list = new ExtractedList(PlaceSourceType.NAVER_MAP, "list-id", "원슐랭",
            null, 106, many);
        given(kakaoPlaceMatcher.match(any())).willReturn(MatchResult.matched(document("111", "가게")));

        MapShareExtractResponse response = service(provider(PlaceSourceType.NAVER_MAP, "naver.me", list))
            .extract(new MapShareExtractRequest("https://naver.me/xAbC1234"));

        assertThat(response.truncated()).isTrue();
        assertThat(response.extractedCount()).isEqualTo(100);
        assertThat(response.declaredCount()).isEqualTo(106);
    }

    @Test
    void extract는_지원하지_않는_링크면_예외를_던진다() {
        ExtractedList list = new ExtractedList(PlaceSourceType.NAVER_MAP, "list-id", "원슐랭",
            null, 0, List.of());

        assertThatThrownBy(() -> service(provider(PlaceSourceType.NAVER_MAP, "naver.me", list))
            .extract(new MapShareExtractRequest("https://example.com/hello")))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.UNSUPPORTED_SHARE_LINK));
    }

    @Test
    void extract는_URL이_아예_없으면_예외를_던진다() {
        ExtractedList list = new ExtractedList(PlaceSourceType.NAVER_MAP, "list-id", "원슐랭",
            null, 0, List.of());

        assertThatThrownBy(() -> service(provider(PlaceSourceType.NAVER_MAP, "naver.me", list))
            .extract(new MapShareExtractRequest("링크 없는 텍스트")))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.UNSUPPORTED_SHARE_LINK));
    }

    @Test
    void extract는_선언된_수가_있는데_파싱_결과가_0개면_구조_변경으로_본다() {
        // given - 구글은 키 없는 배열이라 포맷이 바뀌면 조용히 빈 결과가 나온다
        ExtractedList list = new ExtractedList(PlaceSourceType.GOOGLE_MAP, "list-id", "구글 그룹 테스트",
            "이중희", 3, List.of());

        assertThatThrownBy(() -> service(provider(PlaceSourceType.GOOGLE_MAP, "goo.gl", list))
            .extract(new MapShareExtractRequest("https://maps.app.goo.gl/T6dTd2hZydkZRByg7")))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.SHARE_LIST_STRUCTURE_CHANGED));
    }

    @Test
    void extract는_원래_빈_리스트면_에러가_아니라_빈_응답을_준다() {
        ExtractedList list = new ExtractedList(PlaceSourceType.NAVER_MAP, "list-id", "빈 리스트",
            null, 0, List.of());

        MapShareExtractResponse response = service(provider(PlaceSourceType.NAVER_MAP, "naver.me", list))
            .extract(new MapShareExtractRequest("https://naver.me/xAbC1234"));

        assertThat(response.matched()).isEmpty();
        assertThat(response.unmatched()).isEmpty();
        assertThat(response.extractedCount()).isZero();
    }
}
