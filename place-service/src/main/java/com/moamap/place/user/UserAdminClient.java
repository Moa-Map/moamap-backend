package com.moamap.place.user;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.response.ApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 운영자(관리자) 권한은 user-service만 알고 있어, 그쪽 내부 API로 확인한다.
 *
 * 권한 확인(isAdmin)은 실패하면 막는다(503) — 확인할 수 없을 때 운영자 기능을 열어 주면 안 된다.
 * 운영자 목록(findAdminIds)은 알림 대상일 뿐이라 실패하면 빈 목록으로 물러선다. 신고 자체는 저장돼 운영자 목록에서 보인다.
 */
@Slf4j
@Component
public class UserAdminClient {

    private final RestClient restClient;
    private final UserServiceProperties properties;

    public UserAdminClient(RestClient.Builder builder, UserServiceProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public boolean isAdmin(Long userId) {
        ApiResponse<AdminCheck> response;
        try {
            response = restClient.get()
                .uri(properties.baseUrl() + "/internal/users/{userId}/admin", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<AdminCheck>>() {
                });
        } catch (RestClientException e) {
            log.warn("user-service 운영자 확인에 실패해 요청을 막는다.", e);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        return response != null && response.getData() != null && response.getData().admin();
    }

    public List<Long> findAdminIds() {
        try {
            ApiResponse<List<Long>> response = restClient.get()
                .uri(properties.baseUrl() + "/internal/users/admins")
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<Long>>>() {
                });
            return response == null || response.getData() == null ? List.of() : response.getData();
        } catch (RestClientException e) {
            log.warn("user-service 운영자 목록 조회에 실패해 신고 알림을 보내지 못했다.", e);
            return List.of();
        }
    }

    record AdminCheck(boolean admin) {
    }
}
