package com.moamap.place.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.moamap.place.user.dto.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 06_architect_blueprint_nickname.md 6장 19단계 + 표 E(UserClient 실패 시 동작)를 검증한다.
 * MapClient와 달리 UserClient는 표시용 부가정보만 다루므로, 실패해도 예외를 밖으로 던지지 않고
 * 항상 (빈 가능성이 있는) Map을 반환해야 한다 — 이게 이 클래스 전체의 존재 이유다.
 */
class UserClientTest {

    private static final String BASE_URL = "http://localhost:8081";

    private UserClient client;
    private MockRestServiceServer mockServer;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new UserClient(builder, new UserServiceProperties(BASE_URL));
    }

    @Test
    void findProfiles는_정상_응답이면_id로_매핑된_프로필_Map을_반환한다() {
        // given
        setUp();
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/v1/users/profiles?ids={ids}", "1,2"))
            .andRespond(withSuccess("""
                {"success": true, "data": [
                    {"id": 1, "nickname": "민서", "profileImageUrl": "http://img/1"},
                    {"id": 2, "nickname": "길동", "profileImageUrl": "http://img/2"}
                ]}
                """, MediaType.APPLICATION_JSON));

        // when
        Map<Long, UserProfileResponse> result = client.findProfiles(List.of(1L, 2L));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(1L).nickname()).isEqualTo("민서");
        assertThat(result.get(2L).nickname()).isEqualTo("길동");
        mockServer.verify();
    }

    @Test
    void findProfiles는_응답에_없는_id는_Map에도_없다() {
        // given: 2L을 요청했지만 존재하지 않는/탈퇴한 사용자라 응답 배열에서 누락된 상황
        setUp();
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/v1/users/profiles?ids={ids}", "1,2"))
            .andRespond(withSuccess("""
                {"success": true, "data": [
                    {"id": 1, "nickname": "민서", "profileImageUrl": null}
                ]}
                """, MediaType.APPLICATION_JSON));

        // when
        Map<Long, UserProfileResponse> result = client.findProfiles(List.of(1L, 2L));

        // then
        assertThat(result).hasSize(1);
        assertThat(result).containsOnlyKeys(1L);
    }

    @Test
    void findProfiles는_user_service가_5xx를_반환해도_예외를_던지지_않고_빈_Map을_반환한다() {
        // given: 표 E - 표시용 정보 실패는 축소(degrade)해야지 예외를 전파하면 안 된다
        setUp();
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/v1/users/profiles?ids={ids}", "1"))
            .andRespond(withServerError());

        // when
        Map<Long, UserProfileResponse> result = client.findProfiles(List.of(1L));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findProfiles는_커넥션_실패시에도_예외를_던지지_않고_빈_Map을_반환한다() {
        // given: 타임아웃/커넥션 거부를 흉내낸다
        setUp();
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/v1/users/profiles?ids={ids}", "1"))
            .andRespond(request -> {
                throw new IOException("connection refused");
            });

        // when
        Map<Long, UserProfileResponse> result = client.findProfiles(List.of(1L));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findProfiles는_ids가_비어있으면_HTTP_호출_없이_빈_Map을_반환한다() {
        // given: mockServer에 어떤 expect()도 등록하지 않는다.
        // 클라이언트가 실제로 요청을 보내면 MockRestServiceServer가 "예상치 못한 요청"으로
        // 예외를 던져 이 테스트가 실패한다 - 즉 이 테스트는 "호출 자체가 없음"을 검증한다.
        setUp();

        // when
        Map<Long, UserProfileResponse> result = client.findProfiles(List.of());

        // then
        assertThat(result).isEmpty();
    }
}
