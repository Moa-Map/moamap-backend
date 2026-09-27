package com.moamap.map.client;

import java.nio.charset.StandardCharsets;
import java.util.List;
import com.moamap.map.client.dto.KakaoAddressSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 계약: HTTP 429는 {@link KakaoAddressRateLimitException}, 그 외 실패(5xx/타임아웃 등)는
 * {@link KakaoAddressSearchException}으로 구분해서 던진다 — sync 서비스가 429일 때만 루프 전체를
 * 중단하고, 그 외 실패는 해당 건만 스킵하고 계속 진행해야 하기 때문이다(청사진 3-1 (D)).
 * documents가 빈 배열이면 예외 없이 빈 리스트를 반환한다 — "결과 없음"은 실패가 아니다.
 */
class KakaoAddressClientTest {

    private static final String BASE_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    private KakaoAddressClient client;
    private MockRestServiceServer mockServer;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoAddressClient(builder, new KakaoAddressProperties("test-key", BASE_URL));
    }

    @Test
    void search는_documents의_첫번째_x와_y를_뒤바꾸지_않고_반환한다() {
        setUp();
        // MockRestServiceServer의 queryParam()은 percent-decode 없이 raw query와 비교하므로
        // RestClient가 인코딩하는 것과 동일하게 기대값도 인코딩해서 비교한다.
        mockServer.expect(requestTo(startsWith(BASE_URL)))
            .andExpect(queryParam("query", UriUtils.encodeQueryParam("서울특별시 서초구 강남대로 224", StandardCharsets.UTF_8)))
            .andRespond(withSuccess("""
                {"documents": [{"x": "127.111111", "y": "37.222222"}]}
                """, MediaType.APPLICATION_JSON));

        List<KakaoAddressSearchResponse.Document> result = client.search("서울특별시 서초구 강남대로 224");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).x()).isEqualTo("127.111111");
        assertThat(result.get(0).y()).isEqualTo("37.222222");
        mockServer.verify();
    }

    @Test
    void search는_documents가_비어있으면_빈_리스트를_반환한다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL)))
            .andRespond(withSuccess("""
                {"documents": []}
                """, MediaType.APPLICATION_JSON));

        List<KakaoAddressSearchResponse.Document> result = client.search("존재하지 않는 주소");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void search는_HTTP_429면_KakaoAddressRateLimitException을_던진다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL)))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.search("아무 주소"))
            .isInstanceOf(KakaoAddressRateLimitException.class);
        mockServer.verify();
    }

    @Test
    void search는_429가_아닌_실패는_KakaoAddressSearchException을_던진다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL)))
            .andRespond(withServerError());

        assertThatThrownBy(() -> client.search("아무 주소"))
            .isInstanceOf(KakaoAddressSearchException.class)
            .isNotInstanceOf(KakaoAddressRateLimitException.class);
        mockServer.verify();
    }
}
