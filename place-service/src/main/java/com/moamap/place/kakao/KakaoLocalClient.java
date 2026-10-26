package com.moamap.place.kakao;

import java.math.BigDecimal;
import java.util.List;
import com.moamap.place.kakao.dto.KakaoPlaceDocument;
import com.moamap.place.kakao.dto.KakaoPlaceSearchResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoLocalClient {

    private static final int SEARCH_RESULT_SIZE = 3;

    /** 재매칭은 후보를 몇 개 놓고 이름·거리로 필터링하므로 조금 더 넉넉히 받는다. */
    private static final int NEARBY_RESULT_SIZE = 5;

    /** 검색 반경(m). 채택 임계값(100m)보다 넓게 잡아, 이름은 맞는데 좌표가 어긋난 경우를 TOO_FAR로 구분할 수 있게 한다. */
    private static final int NEARBY_RADIUS_METERS = 1000;

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

    /**
     * 좌표 주변에서 키워드로 검색해 가까운 순으로 받는다.
     * 지도 공유 링크에서 추출한 장소를 카카오 장소와 재매칭할 때 쓴다.
     */
    public List<KakaoPlaceDocument> searchNearby(String query, BigDecimal lng, BigDecimal lat) {
        KakaoPlaceSearchResponse response;
        try {
            response = restClient.get()
                .uri(properties.baseUrl()
                        + "?query={query}&x={x}&y={y}&radius={radius}&sort=distance&size={size}",
                    query, lng.toPlainString(), lat.toPlainString(),
                    NEARBY_RADIUS_METERS, NEARBY_RESULT_SIZE)
                .header("Authorization", "KakaoAK " + properties.restApiKey())
                .retrieve()
                .body(KakaoPlaceSearchResponse.class);
        } catch (RestClientException e) {
            throw new KakaoLocalSearchException("카카오 장소 검색에 실패했습니다.", e);
        }
        return response == null ? List.of() : response.documents();
    }
}
