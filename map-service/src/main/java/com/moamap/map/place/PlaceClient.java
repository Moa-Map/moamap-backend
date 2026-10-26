package com.moamap.map.place;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import com.moamap.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 멤버 목록에 붙일 "등록한 장소 수"를 place-service에서 가져온다.
 *
 * UserClient와 같은 이유로 실패해도 예외를 던지지 않고 빈 Map으로 물러선다.
 * 장소 수를 못 가져왔다고 멤버 목록 전체가 실패하면, place-service 장애가 지도 화면 장애로 번진다.
 *
 * 빈 Map은 "0개"가 아니라 "모름"을 뜻한다. 조회에 성공하면 place-service가 요청한 멤버 전원을
 * 키로 채워주므로, 키가 비어 있다는 것은 곧 조회가 실패했다는 뜻이다.
 */
@Slf4j
@Component
public class PlaceClient {

    private final RestClient restClient;
    private final PlaceServiceProperties properties;

    public PlaceClient(RestClient.Builder builder, PlaceServiceProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public Map<Long, Long> countByCreator(Long mapId, Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        String idsParam = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        ApiResponse<Map<Long, Long>> response;
        try {
            response = restClient.get()
                .uri(properties.baseUrl() + "/api/v1/places/counts?mapId=" + mapId + "&userIds=" + idsParam)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<Map<Long, Long>>>() {
                });
        } catch (RestClientException e) {
            log.warn("place-service 장소 수 조회에 실패해 장소 수 없이 응답한다. mapId={}, 조회 대상 {}명",
                mapId, userIds.size(), e);
            return Map.of();
        }
        if (response == null || response.getData() == null) {
            return Map.of();
        }
        return response.getData();
    }
}
