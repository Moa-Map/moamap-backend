package com.moamap.place.map;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.response.ApiResponse;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.dto.MapMemberResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * map-service에 지도 유형/멤버 역할을 물어보는 클라이언트.
 * 지금은 동기 HTTP 호출로 두고, 트래픽/장애 이슈가 생기면 이벤트 기반 로컬 캐시로 전환을 검토한다.
 */
@Slf4j
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
                .uri(properties.baseUrl() + "/api/v1/maps/{mapId}/members/{userId}", mapId, userId)
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

    /**
     * 지도의 방장·관리자 ID. 장소 신고 알림을 받을 사람이다.
     * 알림 대상일 뿐이라 실패하면 빈 목록으로 물러선다 — 신고 자체는 저장돼 방장·관리자 신고 목록에서 보인다.
     */
    public List<Long> findManagerIds(Long mapId) {
        try {
            ApiResponse<List<Long>> response = restClient.get()
                .uri(properties.baseUrl() + "/internal/maps/{mapId}/managers", mapId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<Long>>>() {
                });
            return response == null || response.getData() == null ? List.of() : response.getData();
        } catch (RestClientException e) {
            log.warn("map-service 방장·관리자 조회에 실패해 장소 신고 알림을 보내지 못했다. mapId={}", mapId, e);
            return List.of();
        }
    }
}
