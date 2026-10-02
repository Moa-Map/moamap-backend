package com.moamap.map.client;

import java.util.List;
import com.moamap.map.client.dto.KakaoAddressSearchResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 카카오 주소 검색 API 클라이언트. sync 서비스가 PENDING 행을 지오코딩할 때 쓴다. */
@Component
public class KakaoAddressClient {

    private final RestClient restClient;
    private final KakaoAddressProperties properties;

    public KakaoAddressClient(RestClient.Builder builder, KakaoAddressProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public List<KakaoAddressSearchResponse.Document> search(String query) {
        KakaoAddressSearchResponse response;
        try {
            response = restClient.get()
                .uri(properties.baseUrl() + "?query={query}", query)
                .header("Authorization", "KakaoAK " + properties.restApiKey())
                .retrieve()
                .body(KakaoAddressSearchResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new KakaoAddressRateLimitException("카카오 주소 검색 호출 한도를 초과했습니다.", e);
        } catch (RestClientException e) {
            throw new KakaoAddressSearchException("카카오 주소 검색에 실패했습니다.", e);
        }
        return response == null || response.documents() == null ? List.of() : response.documents();
    }
}
