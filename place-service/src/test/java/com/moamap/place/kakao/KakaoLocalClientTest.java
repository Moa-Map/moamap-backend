package com.moamap.place.kakao;

import java.util.List;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoLocalClientTest {

    private static final String BASE_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    private KakaoLocalClient client;
    private MockRestServiceServer mockServer;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocalClient(builder, new KakaoLocalProperties("test-key", BASE_URL));
    }

    @Test
    void searchByKeyword는_size를_3으로_요청하고_문서_목록을_반환한다() {
        // given
        setUp();
        mockServer.expect(requestToUriTemplate(BASE_URL + "?query={query}&size={size}", "북촌 프루", 3))
            .andExpect(header("Authorization", "KakaoAK test-key"))
            .andRespond(withSuccess("""
                {"documents": [{"id": "1", "place_name": "프루"}]}
                """, MediaType.APPLICATION_JSON));

        // when
        List<KakaoPlaceDocument> result = client.searchByKeyword("북촌 프루");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).placeName()).isEqualTo("프루");
        mockServer.verify();
    }

    @Test
    void searchByKeyword는_카카오_API가_실패하면_KakaoLocalSearchException을_던진다() {
        // given
        setUp();
        mockServer.expect(requestToUriTemplate(BASE_URL + "?query={query}&size={size}", "테스트", 3))
            .andRespond(withServerError());

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.searchByKeyword("테스트"))
            .isInstanceOf(KakaoLocalSearchException.class);
    }
}
