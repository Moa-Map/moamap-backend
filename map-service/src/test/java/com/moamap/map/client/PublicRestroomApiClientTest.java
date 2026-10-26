package com.moamap.map.client;

import java.util.List;
import com.moamap.map.client.dto.PublicRestroomApiResponse;
import com.moamap.map.client.dto.PublicRestroomItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 계약: resultCode != "0" 또는 HTTP 실패는 둘 다 {@link PublicRestroomApiException}으로 통일해서 던진다.
 * sync 서비스는 이 예외 하나만 잡으면 "이 페이지는 실패했다"를 판단할 수 있다.
 */
class PublicRestroomApiClientTest {

    private static final String BASE_URL = "https://apis.data.go.kr";
    private static final String PATH = "/1741000/TouristToilet/getToiletList";

    private PublicRestroomApiClient client;
    private MockRestServiceServer mockServer;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new PublicRestroomApiClient(builder,
            new PublicRestroomApiProperties(BASE_URL, PATH, "test-service-key"));
    }

    @Test
    void fetchPage은_여러_건이_배열로_와도_정상_파싱한다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL + PATH)))
            .andExpect(queryParam("pageNo", "1"))
            .andExpect(queryParam("numOfRows", "1000"))
            .andRespond(withSuccess("""
                {
                  "response": {
                    "header": {"resultCode": "0", "resultMsg": "OK"},
                    "body": {
                      "numOfRows": 1000, "pageNo": 1, "totalCount": 2,
                      "items": {"item": [
                        {"MNG_NO": "A1", "RSTRM_NM": "첫번째 화장실"},
                        {"MNG_NO": "A2", "RSTRM_NM": "두번째 화장실"}
                      ]}
                    }
                  }
                }
                """, MediaType.APPLICATION_JSON));

        PublicRestroomApiResponse.Body body = client.fetchPage(1, 1000);

        List<PublicRestroomItem> items = body.items().item();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).mngNo()).isEqualTo("A1");
        assertThat(items.get(1).name()).isEqualTo("두번째 화장실");
        assertThat(body.totalCount()).isEqualTo(2);
        mockServer.verify();
    }

    @Test
    void item이_1건이면_배열이_아니라_단일_객체로_와도_리스트로_파싱한다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL + PATH)))
            .andRespond(withSuccess("""
                {
                  "response": {
                    "header": {"resultCode": "0", "resultMsg": "OK"},
                    "body": {
                      "numOfRows": 1000, "pageNo": 1, "totalCount": 1,
                      "items": {"item": {"MNG_NO": "A1", "RSTRM_NM": "단일 화장실"}}
                    }
                  }
                }
                """, MediaType.APPLICATION_JSON));

        PublicRestroomApiResponse.Body body = client.fetchPage(1, 1000);

        assertThat(body.items().item()).hasSize(1);
        assertThat(body.items().item().get(0).mngNo()).isEqualTo("A1");
        mockServer.verify();
    }

    @Test
    void 원천이_모르는_필드를_추가해도_역직렬화가_실패하지_않는다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL + PATH)))
            .andRespond(withSuccess("""
                {
                  "response": {
                    "header": {"resultCode": "0", "resultMsg": "OK"},
                    "body": {
                      "numOfRows": 1000, "pageNo": 1, "totalCount": 1,
                      "items": {"item": {
                        "MNG_NO": "A1", "RSTRM_NM": "화장실",
                        "SFTY_MNG_FCLT_INSTL_TRGT": "Y",
                        "BSS_STT_NM": "정상"
                      }}
                    }
                  }
                }
                """, MediaType.APPLICATION_JSON));

        PublicRestroomApiResponse.Body body = client.fetchPage(1, 1000);

        assertThat(body.items().item().get(0).mngNo()).isEqualTo("A1");
        mockServer.verify();
    }

    @Test
    void resultCode가_0이_아니면_PublicRestroomApiException을_던진다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL + PATH)))
            .andRespond(withSuccess("""
                {
                  "response": {
                    "header": {"resultCode": "30", "resultMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"},
                    "body": {"numOfRows": 1000, "pageNo": 1, "totalCount": 0, "items": {"item": []}}
                  }
                }
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPage(1, 1000))
            .isInstanceOf(PublicRestroomApiException.class);
        mockServer.verify();
    }

    @Test
    void HTTP_실패_응답도_PublicRestroomApiException으로_변환한다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL + PATH)))
            .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchPage(1, 1000))
            .isInstanceOf(PublicRestroomApiException.class);
        mockServer.verify();
    }

    @Test
    void IO_오류가_나도_예외와_원인_체인에_서비스키가_남지_않는다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL + PATH)))
            .andRespond(request -> {
                throw new java.io.IOException("Read timed out");
            });

        assertThatThrownBy(() -> client.fetchPage(1, 1000))
            .isInstanceOf(PublicRestroomApiException.class)
            .satisfies(e -> {
                for (Throwable t = e; t != null; t = t.getCause()) {
                    assertThat(String.valueOf(t.getMessage())).doesNotContain("test-service-key");
                }
            });
    }

    @Test
    void 서비스키에_URI_불가_문자가_있어도_키를_노출하지_않고_PublicRestroomApiException으로_변환한다() {
        RestClient.Builder builder = RestClient.builder();
        PublicRestroomApiClient badKeyClient = new PublicRestroomApiClient(builder,
            new PublicRestroomApiProperties(BASE_URL, PATH, "bad key+/="));

        assertThatThrownBy(() -> badKeyClient.fetchPage(1, 1000))
            .isInstanceOf(PublicRestroomApiException.class)
            .hasNoCause()
            .message().doesNotContain("bad key+/=");
    }

    @Test
    void fetchPage은_pageNo와_numOfRows를_요청_파라미터에_싣는다() {
        setUp();
        mockServer.expect(requestTo(startsWith(BASE_URL + PATH)))
            .andExpect(queryParam("pageNo", "3"))
            .andExpect(queryParam("numOfRows", "50"))
            .andRespond(withSuccess("""
                {
                  "response": {
                    "header": {"resultCode": "0", "resultMsg": "OK"},
                    "body": {"numOfRows": 50, "pageNo": 3, "totalCount": 0, "items": {"item": []}}
                  }
                }
                """, MediaType.APPLICATION_JSON));

        client.fetchPage(3, 50);

        mockServer.verify();
    }
}
