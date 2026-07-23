package com.moamap.place.mapshare;

import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.mapshare.dto.ExtractedList;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import org.junit.jupiter.api.Test;

import static com.moamap.place.mapshare.NaverMapShareProviderTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;

class KakaoMapShareProviderTest {

    private final KakaoMapShareProvider provider = new KakaoMapShareProvider(null);

    @Test
    void supports는_카카오_호스트만_받는다() {
        assertThat(provider.supports("https://kko.to/0FyvknIfua")).isTrue();
        assertThat(provider.supports("https://map.kakao.com/?target=other&folderid=23211144")).isTrue();
        assertThat(provider.supports("https://applink.map.kakao.com/open?page=bookmark&folderid=1")).isTrue();
        assertThat(provider.supports("https://naver.me/xAbC1234")).isFalse();
    }

    @Test
    void findFolderId는_folderid_파라미터를_뽑는다() {
        assertThat(KakaoMapShareProvider.findFolderId(
            "https://map.kakao.com/?target=other&folderid=23211144")).isEqualTo("23211144");
    }

    @Test
    void findFolderId는_대소문자를_가리지_않는다() {
        // folder/info는 folderId(대문자 I), favorite/list는 folderid(소문자 i)로 표기가 다르다.
        assertThat(KakaoMapShareProvider.findFolderId(
            "https://map.kakao.com/?folderId=23211144")).isEqualTo("23211144");
    }

    @Test
    void findFolderId는_없으면_null을_반환한다() {
        assertThat(KakaoMapShareProvider.findFolderId("https://kko.to/0FyvknIfua")).isNull();
    }

    @Test
    void parse는_폴더_정보에서_이름과_작성자와_선언된_수를_읽는다() {
        ExtractedList result = KakaoMapShareProvider.parse(
            fixture("kakao-folder.json"), fixture("kakao-favorites.json"), "23211144");

        assertThat(result.source()).isEqualTo(PlaceSourceType.KAKAO_MAP);
        assertThat(result.listId()).isEqualTo("23211144");
        assertThat(result.listName()).isEqualTo("그룹그룹그룹");
        assertThat(result.owner()).isEqualTo("이중희");
        assertThat(result.declaredCount()).isEqualTo(2);
    }

    @Test
    void parse는_즐겨찾기_목록에서_장소를_읽는다() {
        ExtractedList result = KakaoMapShareProvider.parse(
            fixture("kakao-folder.json"), fixture("kakao-favorites.json"), "23211144");

        assertThat(result.places()).hasSize(2);
        assertThat(result.places()).extracting(ExtractedMapPlace::name)
            .containsExactly("숭실대학교", "청년다방 서울숭실대점");

        ExtractedMapPlace first = result.places().get(0);
        assertThat(first.address()).isEqualTo("서울 동작구 상도로 369 (상도동)");
        assertThat(first.placeId()).isEqualTo("11124718");
        assertThat(first.lat()).isEqualByComparingTo("37.49585303");
        assertThat(first.lng()).isEqualByComparingTo("126.95781764");
    }

    @Test
    void parse는_카카오가_분류를_주지_않으므로_category를_비운다() {
        ExtractedList result = KakaoMapShareProvider.parse(
            fixture("kakao-folder.json"), fixture("kakao-favorites.json"), "23211144");

        assertThat(result.places()).allSatisfy(place -> assertThat(place.category()).isNull());
    }

    @Test
    void parse는_빈_문자열_메모를_null로_정규화한다() {
        ExtractedList result = KakaoMapShareProvider.parse(
            fixture("kakao-folder.json"), fixture("kakao-favorites.json"), "23211144");

        assertThat(result.places().get(0).memo()).isNull();
    }
}
