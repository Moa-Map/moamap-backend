package com.moamap.place.user;

import com.moamap.common.response.ApiResponse;
import com.moamap.place.user.dto.UserProfileResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * MapClient(권한 판정)와 달리 이 호출은 표시용 부가정보라, 실패해도 예외를 던지지 않고
 * 빈 Map으로 축소(degrade)한다 — user-service 장애가 place-service 기능 장애로 전파되면 안 된다.
 */
@Slf4j
@Component
public class UserClient {

    private final RestClient restClient;
    private final UserServiceProperties properties;

    public UserClient(RestClient.Builder builder, UserServiceProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public Map<Long, UserProfileResponse> findProfiles(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String idsParam = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        ApiResponse<List<UserProfileResponse>> response;
        try {
            // URI 템플릿 변수로 넘기면 값 단위 인코딩 때문에 쉼표가 %2C로 바뀐다. 문자열로 직접 이어붙인다.
            response = restClient.get()
                .uri(properties.baseUrl() + "/api/v1/users/profiles?ids=" + idsParam)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<UserProfileResponse>>>() {
                });
        } catch (RestClientException e) {
            log.warn("user-service 프로필 조회 실패, 닉네임 없이 진행합니다.", e);
            return Map.of();
        }
        if (response == null || response.getData() == null) {
            return Map.of();
        }
        Map<Long, UserProfileResponse> result = new HashMap<>();
        for (UserProfileResponse profile : response.getData()) {
            result.put(profile.id(), profile);
        }
        return result;
    }
}
