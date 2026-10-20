package com.moamap.notification.user;

import com.moamap.common.response.ApiResponse;
import com.moamap.notification.user.dto.UserProfileResponse;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 알림 목록에 붙일 닉네임·프로필 이미지를 user-service에서 가져온다.
 *
 * 실패는 UserProfiles.unavailable()로 돌려준다. 빈 결과와 구분해야 정상 회원이 탈퇴한 사용자로 보이지 않는다.
 *
 * 표시용 부가정보라 실패해도 예외를 던지지 않는다. user-service 장애가 알림 화면 장애로 번지지 않게 한다.
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

    public UserProfiles findProfiles(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return UserProfiles.of(List.of());
        }
        String idsParam = ids.stream().map(String::valueOf).collect(Collectors.joining(","));

        ApiResponse<List<UserProfileResponse>> response;
        try {
            // URI 템플릿 변수로 넘기면 쉼표가 %2C로 인코딩돼 서버가 한 덩어리로 읽는다. 숫자 ID만 직접 잇는다.
            response = restClient.get()
                .uri(properties.baseUrl() + "/api/v1/users/profiles?ids=" + idsParam)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<UserProfileResponse>>>() {
                });
        } catch (RestClientException e) {
            log.warn("user-service 프로필 조회에 실패해 닉네임 없이 응답한다. 조회 대상 {}명", ids.size(), e);
            return UserProfiles.unavailable();
        }
        if (response == null || response.getData() == null) {
            return UserProfiles.unavailable();
        }
        return UserProfiles.of(response.getData());
    }
}
