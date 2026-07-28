package com.moamap.map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.storage.ObjectStoragePresigner;
import com.moamap.common.storage.PresignedUploadUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 지도 커버 업로드 URL 발급 API의 계약(상태 코드·응답 모양)을 검증한다.
 *
 * 실제 스토리지에 붙지 않도록 발급 유틸은 대역으로 바꾼다. 서명 자체는 AWS SDK의 몫이고
 * 여기서 확인할 것은 "요청이 검증을 거쳐 발급 결과가 그대로 응답에 실리는가"다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MapCoverUploadUrlApiTest {

    private static final String PATH = "/api/v1/maps/cover-upload-url";
    private static final String USER_HEADER = "X-User-Id";
    private static final long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ObjectStoragePresigner presigner;

    @Test
    @DisplayName("발급에 성공하면 업로드 주소와 조회 주소를 함께 돌려준다")
    void issuesUploadUrl() throws Exception {
        given(presigner.presign(anyString(), anyString(), anyLong()))
            .willReturn(new PresignedUploadUrl(
                "https://storage.example/put?sig=x",
                "map-covers/1/uuid.jpg",
                "https://storage.example/map-covers/1/uuid.jpg",
                300L));

        mockMvc.perform(post(PATH)
                .header(USER_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("image/jpeg", 1024L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.uploadUrl").value("https://storage.example/put?sig=x"))
            .andExpect(jsonPath("$.data.objectKey").value("map-covers/1/uuid.jpg"))
            .andExpect(jsonPath("$.data.fileUrl").value("https://storage.example/map-covers/1/uuid.jpg"))
            .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
    }

    @Test
    @DisplayName("허용되지 않은 형식은 400과 사유를 돌려준다")
    void rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(post(PATH)
                .header(USER_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("image/gif", 1024L)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MAP_010"));
    }

    @Test
    @DisplayName("최대 크기를 넘으면 400과 사유를 돌려준다")
    void rejectsOversizedFile() throws Exception {
        mockMvc.perform(post(PATH)
                .header(USER_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("image/jpeg", 10L * 1024 * 1024 + 1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MAP_011"));
    }

    @Test
    @DisplayName("필수 값이 없으면 400을 돌려준다")
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post(PATH)
                .header(USER_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}".getBytes(StandardCharsets.UTF_8)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 돌려준다")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("image/jpeg", 1024L)))
            .andExpect(status().isUnauthorized());
    }

    private byte[] body(String contentType, long fileSize) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentType", contentType);
        body.put("fileSize", fileSize);
        return objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }
}
