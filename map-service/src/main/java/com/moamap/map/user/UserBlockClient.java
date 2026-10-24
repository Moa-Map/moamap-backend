package com.moamap.map.user;

import com.moamap.common.response.ApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 요청한 사람이 차단한 사용자 ID를 user-service 내부 API로 가져온다. 목록에서 그 사람들의 콘텐츠를 빼는 데 쓴다.
 *
 * 실패하면 빈 목록으로 물러선다(차단 필터 없이 목록을 보여준다). 차단 목록을 못 가져왔다고 지도 화면 전체가
 * 막히면 user-service 장애가 이 서비스 장애로 번진다. 앱도 받아 둔 차단 목록으로 화면에서 한 번 더 거른다.
 */
@Slf4j
@Component
public class UserBlockClient {

    private final RestClient restClient;
    private final UserServiceProperties properties;

    public UserBlockClient(RestClient.Builder builder, UserServiceProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    /** 로그인하지 않았으면 차단한 사람이 없다. */
    public List<Long> findBlockedIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            ApiResponse<List<Long>> response = restClient.get()
                .uri(properties.baseUrl() + "/internal/users/{userId}/blocked-ids", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<Long>>>() {
                });
            return response == null || response.getData() == null ? List.of() : response.getData();
        } catch (RestClientException e) {
            log.warn("user-service 차단 목록 조회에 실패해 차단 필터 없이 목록을 보여준다.", e);
            return List.of();
        }
    }
}
