package com.moamap.user.banner.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.banner.dto.BannerResponse;
import com.moamap.user.banner.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Banner", description = "메인 배너 조회 API")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(summary = "메인 배너 목록", description = "노출 순서대로 내려준다(최대 10개). 로그인 없이 조회할 수 있다. 배너가 없으면 빈 목록이다.")
    @GetMapping
    public ApiResponse<List<BannerResponse>> getBanners() {
        return ApiResponse.success(bannerService.getBanners());
    }
}
