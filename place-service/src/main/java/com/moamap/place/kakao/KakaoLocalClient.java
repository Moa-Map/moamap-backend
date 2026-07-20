package com.moamap.place.kakao;

import java.util.List;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import com.moamap.place.kakao.dto.KakaoPlaceSearchResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoLocalClient {

    private static final int SEARCH_RESULT_SIZE = 3;

    private final RestClient restClient;
    private final KakaoLocalProperties properties;

    public KakaoLocalClient(RestClient.Builder builder, KakaoLocalProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public List<KakaoPlaceDocument> searchByKeyword(String query) {
        KakaoPlaceSearchResponse response;
        try {
            response = restClient.get()
                .uri(properties.baseUrl() + "?query={query}&size={size}", query, SEARCH_RESULT_SIZE)
                .header("Authorization", "KakaoAK " + properties.restApiKey())
                .retrieve()
                .body(KakaoPlaceSearchResponse.class);
        } catch (RestClientException e) {
            throw new KakaoLocalSearchException("카카오 장소 검색에 실패했습니다.", e);
        }
        return response == null ? List.of() : response.documents();
    }
}
