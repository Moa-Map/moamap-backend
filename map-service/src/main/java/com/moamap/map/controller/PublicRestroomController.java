package com.moamap.map.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.map.dto.PublicRestroomDetailResponse;
import com.moamap.map.dto.PublicRestroomListResponse;
import com.moamap.map.dto.RestroomBoundsRequest;
import com.moamap.map.service.PublicRestroomQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maps/official/restrooms")
@RequiredArgsConstructor
public class PublicRestroomController {

    private final PublicRestroomQueryService queryService;

    @GetMapping
    public ApiResponse<PublicRestroomListResponse> listMarkers(@Valid RestroomBoundsRequest request) {
        return ApiResponse.success(queryService.listMarkers(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicRestroomDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.success(queryService.getDetail(id));
    }
}
