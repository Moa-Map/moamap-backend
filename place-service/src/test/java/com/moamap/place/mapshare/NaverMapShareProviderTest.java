package com.moamap.place.mapshare;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.mapshare.dto.ExtractedList;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NaverMapShareProviderTest {

    private final NaverMapShareProvider provider = new NaverMapShareProvider(null);

    static String fixture(String name) {
        try (InputStream in = NaverMapShareProviderTest.class.getResourceAsStream(
            "/fixtures/mapshare/" + name)) {
            if (in == null) {
                throw new IllegalStateException("픽스처를 찾을 수 없습니다: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void supports는_네이버_호스트만_받는다() {
        assertThat(provider.supports("https://naver.me/xAbC1234")).isTrue();
        assertThat(provider.supports("https://map.naver.com/p/favorite/myPlace")).isTrue();
        assertThat(provider.supports("https://kko.to/0FyvknIfua")).isFalse();
        assertThat(provider.supports("https://maps.app.goo.gl/abc")).isFalse();
    }

    @Test
    void supports는_호스트가_아닌_경로_쿼리에_도메인이_섞여도_거부한다() {
        // SSRF 방지: 부분 문자열이 아니라 실제 호스트로 판정해야 한다.
        assertThat(provider.supports("http://169.254.169.254/latest/meta-data/?x=map.naver.com")).isFalse();
        assertThat(provider.supports("http://evil.com/naver.me")).isFalse();
        assertThat(provider.supports("http://naver.me.evil.com/path")).isFalse();
    }

    @Test
    void findShareId는_쿼리스트링의_id에서_32자리_hex를_뽑는다() {
        assertThat(NaverMapShareProvider.findShareId(
            "https://map.naver.com/p?id=189963DE7AF14407A72B4316370DEDE5"))
            .isEqualTo("189963de7af14407a72b4316370dede5");
    }

    @Test
    void findShareId는_detail_list_경로에서도_뽑는다() {
        assertThat(NaverMapShareProvider.findShareId(
            "https://pages.map.naver.com/save-pages/web/detail-list/189963de7af14407a72b4316370dede5"))
            .isEqualTo("189963de7af14407a72b4316370dede5");
    }

    @Test
    void findShareId는_URL_인코딩된_경로에서도_뽑는다() {
        assertThat(NaverMapShareProvider.findShareId(
            "https://m.map.naver.com/?fallbackUrl=detail-list%2F189963de7af14407a72b4316370dede5"))
            .isEqualTo("189963de7af14407a72b4316370dede5");
    }

    @Test
    void findShareId는_없으면_null을_반환한다() {
        assertThat(NaverMapShareProvider.findShareId("https://naver.me/xAbC1234")).isNull();
    }

    @Test
    void parse는_리스트_이름과_선언된_장소_수를_읽는다() {
        ExtractedList result = NaverMapShareProvider.parse(fixture("naver-bookmarks.json"), "shareid");

        assertThat(result.source()).isEqualTo(PlaceSourceType.NAVER_MAP);
        assertThat(result.listId()).isEqualTo("shareid");
        assertThat(result.listName()).isEqualTo("원슐랭");
        assertThat(result.declaredCount()).isEqualTo(106);
        assertThat(result.places()).hasSize(106);
    }

    @Test
    void parse는_장소의_이름_주소_분류_좌표_ID를_채운다() {
        ExtractedList result = NaverMapShareProvider.parse(fixture("naver-bookmarks.json"), "shareid");

        ExtractedMapPlace first = result.places().get(0);
        assertThat(first.name()).isEqualTo("테니 하우스");
        assertThat(first.address()).isEqualTo("서울 성동구 뚝섬로17가길 55");
        assertThat(first.category()).isEqualTo("카페");
        assertThat(first.placeId()).isEqualTo("1965277191");
        // py가 위도, px가 경도다. 순서가 뒤집히기 쉬운 지점이라 값으로 못박아 둔다.
        assertThat(first.lat()).isEqualByComparingTo("37.5255661");
        assertThat(first.lng()).isEqualByComparingTo("127.0260696");
    }

    @Test
    void parse는_메모가_null이면_null로_둔다() {
        ExtractedList result = NaverMapShareProvider.parse(fixture("naver-bookmarks.json"), "shareid");

        assertThat(result.places().get(0).memo()).isNull();
    }

    @Test
    void parse는_owner를_채우지_않는다() {
        // 네이버는 작성자 닉네임을 마스킹해서 준다(wk****). 쓸모가 없어 버린다.
        ExtractedList result = NaverMapShareProvider.parse(fixture("naver-bookmarks.json"), "shareid");

        assertThat(result.owner()).isNull();
    }
}
