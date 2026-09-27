package com.moamap.map.client;

import java.net.URI;
import com.moamap.map.client.dto.PublicRestroomApiResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 공공화장실 목록 API(data.go.kr) 클라이언트. sync 서비스가 페이지 단위로 반복 호출한다. */
@Component
public class PublicRestroomApiClient {

    private final RestClient restClient;
    private final PublicRestroomApiProperties properties;

    public PublicRestroomApiClient(RestClient.Builder builder, PublicRestroomApiProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    public PublicRestroomApiResponse.Body fetchPage(int pageNo, int numOfRows) {
        PublicRestroomApiResponse response;
        try {
            // data.go.kr의 서비스키는 발급 시점에 이미 percent-encode된 값("Encoding 키")이다.
            // UriComponentsBuilder의 템플릿 변수 확장({key})은 넘어온 값을 다시 encode하므로,
            // 그대로 넘기면 %2B -> %252B 처럼 이중 인코딩되어 인증이 항상 실패한다.
            // URI를 직접 만들어(.uri(URI))로 넘기면 Spring이 추가 인코딩을 하지 않는다.
            URI uri = URI.create(properties.baseUrl() + properties.path()
                + "?serviceKey=" + properties.serviceKey()
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows
                + "&type=json");
            response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(PublicRestroomApiResponse.class);
        } catch (IllegalArgumentException e) {
            // URI.create의 예외 메시지는 serviceKey가 든 전체 URI를 담는다 — cause로 넘기면 로그에 키가 평문으로 남는다.
            throw new PublicRestroomApiException("공공화장실 API URI를 만들 수 없습니다. 서비스키가 인코딩 키인지 확인하세요.");
        } catch (RestClientException e) {
            // RestClient 예외 메시지는 쿼리스트링을 뺀 URI만 담아 serviceKey가 노출되지 않는다.
            throw new PublicRestroomApiException("공공화장실 API 호출에 실패했습니다.", e);
        }

        if (response == null || response.response() == null || response.response().header() == null
                || !"0".equals(response.response().header().resultCode())) {
            String resultMsg = response == null || response.response() == null || response.response().header() == null
                ? "empty response"
                : response.response().header().resultMsg();
            throw new PublicRestroomApiException("공공화장실 API가 오류를 반환했습니다: " + resultMsg);
        }

        return response.response().body();
    }
}
