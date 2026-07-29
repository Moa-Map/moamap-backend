package com.moamap.map.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.moamap.common.response.ApiResponse;
import com.moamap.map.user.dto.UserProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 멤버 목록에 붙일 닉네임·프로필 이미지를 user-service에서 가져온다.
 *
 * 표시용 부가정보라 실패해도 예외를 던지지 않고 빈 Map으로 물러선다.
 * 닉네임을 못 가져왔다고 멤버 목록 전체가 실패하면, user-service 장애가 지도 화면 장애로 번진다.
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
            // URI 템플릿 변수로 넘기면 쉼표가 %2C로 인코딩돼 서버가 한 덩어리로 읽는다. 문자열로 직접 잇는다.
            response = restClient.get()
                .uri(properties.baseUrl() + "/api/v1/users/profiles?ids=" + idsParam)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<UserProfileResponse>>>() {
                });
        } catch (RestClientException e) {
            log.warn("user-service 프로필 조회에 실패해 닉네임 없이 응답한다. 조회 대상 {}명", ids.size(), e);
            return Map.of();
        }
        if (response == null || response.getData() == null) {
            return Map.of();
        }

        Map<Long, UserProfileResponse> profilesById = new HashMap<>();
        for (UserProfileResponse profile : response.getData()) {
            profilesById.put(profile.id(), profile);
        }
        return profilesById;
    }
}
