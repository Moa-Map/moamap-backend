package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.List;
import com.moamap.place.ai.GeminiPlaceExtractor;
import com.moamap.place.ai.dto.ExtractedPlace;
import com.moamap.place.dto.InstagramExtractRequest;
import com.moamap.place.dto.PlaceCandidateResponse;
import com.moamap.place.kakao.KakaoLocalClient;
import com.moamap.place.kakao.KakaoLocalSearchException;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class PlaceExtractionServiceTest {

    @Mock
    private GeminiPlaceExtractor geminiPlaceExtractor;

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    @InjectMocks
    private PlaceExtractionService placeExtractionService;

    private KakaoPlaceDocument document(String id, String name) {
        return new KakaoPlaceDocument(id, name, "음식점 > 카페", "CE7", "카페",
            "서울 종로구", "서울 종로구 북촌로", "126.98", "37.58", "http://place.map.kakao.com/" + id);
    }

    @Test
    void extractFromInstagram은_추출된_모든_장소의_카카오_검색_결과를_병합한다() {
        // given
        InstagramExtractRequest request = new InstagramExtractRequest("https://instagram.com/p/1", "설명글");
        given(geminiPlaceExtractor.extract("설명글")).willReturn(List.of(
            new ExtractedPlace("프루", "북촌"),
            new ExtractedPlace("쿄와우동", "계동")
        ));
        given(kakaoLocalClient.searchByKeyword("북촌 프루")).willReturn(List.of(document("1", "프루")));
        given(kakaoLocalClient.searchByKeyword("계동 쿄와우동")).willReturn(List.of(document("2", "쿄와우동")));

        // when
        List<PlaceCandidateResponse> result = placeExtractionService.extractFromInstagram(request);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlaceCandidateResponse::name).containsExactlyInAnyOrder("프루", "쿄와우동");
        assertThat(result).allMatch(candidate -> candidate.sourceUrl().equals("https://instagram.com/p/1"));
    }

    @Test
    void extractFromInstagram은_같은_kakaoPlaceId를_중복없이_하나로_합친다() {
        // given
        InstagramExtractRequest request = new InstagramExtractRequest("https://instagram.com/p/1", "설명글");
        given(geminiPlaceExtractor.extract("설명글")).willReturn(List.of(new ExtractedPlace("프루", "북촌")));
        given(kakaoLocalClient.searchByKeyword("북촌 프루")).willReturn(List.of(
            document("1", "프루"), document("1", "프루")
        ));

        // when
        List<PlaceCandidateResponse> result = placeExtractionService.extractFromInstagram(request);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    void extractFromInstagram은_카카오_검색이_실패한_장소는_건너뛰고_나머지는_정상_반환한다() {
        // given
        InstagramExtractRequest request = new InstagramExtractRequest("https://instagram.com/p/1", "설명글");
        given(geminiPlaceExtractor.extract("설명글")).willReturn(List.of(
            new ExtractedPlace("장애나는곳", "종로"),
            new ExtractedPlace("쿄와우동", "계동")
        ));
        willThrow(new KakaoLocalSearchException("카카오 장소 검색에 실패했습니다.", new RuntimeException("boom")))
            .given(kakaoLocalClient).searchByKeyword("종로 장애나는곳");
        given(kakaoLocalClient.searchByKeyword("계동 쿄와우동")).willReturn(List.of(document("2", "쿄와우동")));

        // when
        List<PlaceCandidateResponse> result = placeExtractionService.extractFromInstagram(request);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("쿄와우동");
    }

    @Test
    void extractFromInstagram은_이름을_추출하지_못한_장소는_카카오_검색없이_건너뛴다() {
        // given
        InstagramExtractRequest request = new InstagramExtractRequest("https://instagram.com/p/1", "설명글");
        given(geminiPlaceExtractor.extract("설명글")).willReturn(List.of(new ExtractedPlace(null, null)));

        // when
        List<PlaceCandidateResponse> result = placeExtractionService.extractFromInstagram(request);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractFromInstagram은_추출된_장소가_없으면_빈_목록을_반환한다() {
        // given
        InstagramExtractRequest request = new InstagramExtractRequest("https://instagram.com/p/1", "설명글");
        given(geminiPlaceExtractor.extract("설명글")).willReturn(List.of());

        // when
        List<PlaceCandidateResponse> result = placeExtractionService.extractFromInstagram(request);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractFromInstagram은_kakaoPlaceId가_없는_후보도_그대로_포함한다() {
        // given
        InstagramExtractRequest request = new InstagramExtractRequest("https://instagram.com/p/1", "설명글");
        given(geminiPlaceExtractor.extract("설명글")).willReturn(List.of(new ExtractedPlace("프루", "북촌")));
        given(kakaoLocalClient.searchByKeyword("북촌 프루")).willReturn(List.of(
            new KakaoPlaceDocument(null, "프루", "카페", "CE7", "카페", "서울 종로구", "서울 종로구 북촌로",
                "126.98", "37.58", "http://place.map.kakao.com/unknown")
        ));

        // when
        List<PlaceCandidateResponse> result = placeExtractionService.extractFromInstagram(request);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).kakaoPlaceId()).isNull();
    }

    @Test
    void extractFromInstagram은_좌표를_BigDecimal로_변환한다() {
        // given
        InstagramExtractRequest request = new InstagramExtractRequest("https://instagram.com/p/1", "설명글");
        given(geminiPlaceExtractor.extract("설명글")).willReturn(List.of(new ExtractedPlace("프루", "북촌")));
        given(kakaoLocalClient.searchByKeyword("북촌 프루")).willReturn(List.of(document("1", "프루")));

        // when
        List<PlaceCandidateResponse> result = placeExtractionService.extractFromInstagram(request);

        // then
        assertThat(result.get(0).lat()).isEqualByComparingTo(new BigDecimal("37.58"));
        assertThat(result.get(0).lng()).isEqualByComparingTo(new BigDecimal("126.98"));
    }
}
