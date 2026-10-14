package com.moamap.user.banner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.user.banner.dto.AdminBannerResponse;
import com.moamap.user.banner.dto.BannerResponse;
import com.moamap.user.banner.service.BannerAdminService;
import com.moamap.user.banner.service.BannerService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 입력 검증과 응답 형식. 비즈니스 규칙은 BannerServiceTest가 다룬다.
 */
@WebMvcTest({BannerController.class, BannerAdminController.class})
class BannerControllerTest {

    private static final Instant AT = Instant.parse("2026-10-15T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BannerService bannerService;
    @MockitoBean private BannerAdminService bannerAdminService;

    @Test
    void 앱_배너_목록은_로그인_없이_순서대로_내려준다() throws Exception {
        given(bannerService.getBanners()).willReturn(List.of(
                new BannerResponse(2L, "https://photos.example.com/banners/b.jpg"),
                new BannerResponse(1L, "https://photos.example.com/banners/a.jpg")));

        mockMvc.perform(get("/api/v1/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bannerId").value(2))
                .andExpect(jsonPath("$.data[1].bannerId").value(1));
    }

    @Test
    void 등록에_성공하면_201이다() throws Exception {
        given(bannerAdminService.create(eq(1L), any()))
                .willReturn(new AdminBannerResponse(3L, "https://photos.example.com/banners/c.jpg", 0, AT, AT));

        mockMvc.perform(post("/api/v1/admin/banners").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://photos.example.com/banners/c.jpg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bannerId").value(3));
    }

    @Test
    void 이미지_주소가_비어_있으면_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/banners").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bannerAdminService);
    }

    @Test
    void 순서_변경에_빈_목록이나_10개_초과는_거부한다() throws Exception {
        mockMvc.perform(put("/api/v1/admin/banners/order").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerIds\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/banners/order").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerIds\":[1,2,3,4,5,6,7,8,9,10,11]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bannerAdminService);
    }

    @Test
    void 깨진_JSON은_500이_아니라_400이다() throws Exception {
        mockMvc.perform(put("/api/v1/admin/banners/order").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerIds\":[1,"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 업로드_주소_발급에_파일_크기가_없으면_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/banners/image-upload-url").header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자_API는_인증_정보가_없으면_401이다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/banners/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(bannerAdminService);
    }

    @Test
    void 관리자가_아니면_403이다() throws Exception {
        given(bannerAdminService.getBanners(2L)).willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/admin/banners").header("X-User-Id", 2L))
                .andExpect(status().isForbidden());
    }
}
