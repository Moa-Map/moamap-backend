package com.moamap.place.mapshare;

import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.mapshare.dto.ExtractedList;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import org.junit.jupiter.api.Test;

import static com.moamap.place.mapshare.NaverMapShareProviderTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;

class GoogleMapShareProviderTest {

    private final GoogleMapShareProvider provider = new GoogleMapShareProvider(null);

    @Test
    void supports는_구글_호스트만_받는다() {
        assertThat(provider.supports("https://maps.app.goo.gl/T6dTd2hZydkZRByg7")).isTrue();
        assertThat(provider.supports("https://www.google.com/maps/@/data=!4m3")).isTrue();
        assertThat(provider.supports("https://naver.me/xAbC1234")).isFalse();
    }

    @Test
    void supports는_호스트가_아닌_경로_쿼리에_도메인이_섞여도_거부한다() {
        // SSRF 방지: 부분 문자열이 아니라 실제 호스트로 판정해야 한다.
        assertThat(provider.supports("http://169.254.169.254/maps?x=google.com")).isFalse();
        assertThat(provider.supports("http://evil.com/goo.gl/maps")).isFalse();
        assertThat(provider.supports("http://goo.gl.evil.com/maps")).isFalse();
    }

    @Test
    void findListId는_data_파라미터의_2s_뒤_값을_뽑는다() {
        assertThat(GoogleMapShareProvider.findListId(
            "https://www.google.com/maps/@/data=!4m3!11m2!2s7HluZjfYJKBw0-5fFiBQRY6bISz_yQ!3e3"))
            .isEqualTo("7HluZjfYJKBw0-5fFiBQRY6bISz_yQ");
    }

    @Test
    void findListId는_없으면_null을_반환한다() {
        assertThat(GoogleMapShareProvider.findListId("https://maps.app.goo.gl/T6dTd2hZydkZRByg7")).isNull();
    }

    @Test
    void stripPrefix는_XSSI_방어_프리픽스를_제거한다() {
        assertThat(GoogleMapShareProvider.stripPrefix(")]}'\n[1,2]")).isEqualTo("\n[1,2]");
        assertThat(GoogleMapShareProvider.stripPrefix("[1,2]")).isEqualTo("[1,2]");
    }

    @Test
    void parse는_리스트_이름과_작성자와_선언된_수를_읽는다() {
        ExtractedList result = GoogleMapShareProvider.parse(fixture("google-list.json"), "listid");

        assertThat(result.source()).isEqualTo(PlaceSourceType.GOOGLE_MAP);
        assertThat(result.listName()).isEqualTo("구글 그룹 테스트");
        assertThat(result.owner()).isEqualTo("이중희");
        assertThat(result.declaredCount()).isEqualTo(3);
    }

    @Test
    void parse는_중첩_배열_인덱스로_장소를_읽는다() {
        ExtractedList result = GoogleMapShareProvider.parse(fixture("google-list.json"), "listid");

        assertThat(result.places()).hasSize(3);
        assertThat(result.places()).extracting(ExtractedMapPlace::name)
            .containsExactly("숭실대학교", "압구정 로데오거리", "강남역사거리");

        ExtractedMapPlace first = result.places().get(0);
        assertThat(first.address()).isEqualTo("서울특별시 동작구 상도로 369");
        assertThat(first.lat()).isEqualByComparingTo("37.4963538");
        assertThat(first.lng()).isEqualByComparingTo("126.95722219999999");
    }

    @Test
    void parse는_CID를_쌍의_두_번째_값에서_읽는다() {
        // [1][6]은 두 개짜리 배열이고 CID는 두 번째다. 첫 번째를 쓰면 열리지 않는 링크가 된다.
        ExtractedList result = GoogleMapShareProvider.parse(fixture("google-list.json"), "listid");

        assertThat(result.places().get(0).placeId()).isEqualTo("3247361918594846147");
    }

    @Test
    void parse는_구글이_분류를_주지_않으므로_category를_비운다() {
        ExtractedList result = GoogleMapShareProvider.parse(fixture("google-list.json"), "listid");

        assertThat(result.places()).allSatisfy(place -> assertThat(place.category()).isNull());
    }
}
