package com.moamap.place.mapshare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShareLinkUrlParserTest {

    @Test
    void firstUrl은_URL만_있는_문자열에서_그대로_뽑는다() {
        assertThat(ShareLinkUrlParser.firstUrl("https://naver.me/xAbC1234"))
            .isEqualTo("https://naver.me/xAbC1234");
    }

    @Test
    void firstUrl은_앞뒤에_문구가_섞인_공유_텍스트에서_URL만_뽑는다() {
        String shared = "[네이버 지도]\n원슐랭 리스트 공유합니다 https://naver.me/xAbC1234 확인해줘";

        assertThat(ShareLinkUrlParser.firstUrl(shared)).isEqualTo("https://naver.me/xAbC1234");
    }

    @Test
    void firstUrl은_URL_끝에_붙은_문장부호를_떼어낸다() {
        assertThat(ShareLinkUrlParser.firstUrl("여기야 https://kko.to/0FyvknIfua."))
            .isEqualTo("https://kko.to/0FyvknIfua");
        assertThat(ShareLinkUrlParser.firstUrl("(https://kko.to/0FyvknIfua)"))
            .isEqualTo("https://kko.to/0FyvknIfua");
    }

    @Test
    void firstUrl은_URL이_여러_개면_첫_번째를_고른다() {
        String shared = "https://naver.me/aaaa 랑 https://kko.to/bbbb 둘 다 있음";

        assertThat(ShareLinkUrlParser.firstUrl(shared)).isEqualTo("https://naver.me/aaaa");
    }

    @Test
    void firstUrl은_URL이_없으면_null을_반환한다() {
        assertThat(ShareLinkUrlParser.firstUrl("링크 없는 그냥 텍스트")).isNull();
        assertThat(ShareLinkUrlParser.firstUrl("")).isNull();
        assertThat(ShareLinkUrlParser.firstUrl(null)).isNull();
    }
}
