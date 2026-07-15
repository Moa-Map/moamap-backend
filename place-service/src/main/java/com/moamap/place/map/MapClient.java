package com.moamap.place.map;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.response.ApiResponse;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.dto.MapMemberResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * map-service에 지도 유형/멤버 역할을 물어보는 클라이언트.
 * 지금은 동기 HTTP 호출로 두고, 트래픽/장애 이슈가 생기면 이벤트 기반 로컬 캐시로 전환을 검토한다.
 */
@Component
public class MapClient {

    private final RestClient restClient;
    private final MapServiceProperties properties;

    public MapClient(RestClient.Builder builder, MapServiceProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public MapMemberResponse getMemberInfo(Long mapId, Long userId) {
        ApiResponse<MapMemberResponse> response;
        try {
            response = restClient.get()
                .uri(properties.baseUrl() + "/maps/{mapId}/members/{userId}", mapId, userId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<MapMemberResponse>>() {
                });
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(PlaceErrorCode.MAP_NOT_FOUND);
        } catch (RestClientException e) {
            throw new BusinessException(PlaceErrorCode.MAP_SERVICE_UNAVAILABLE);
        }
        if (response == null || response.getData() == null) {
            throw new BusinessException(PlaceErrorCode.MAP_NOT_FOUND);
        }
        return response.getData();
    }
}
