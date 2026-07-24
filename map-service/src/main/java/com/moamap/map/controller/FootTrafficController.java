package com.moamap.map.controller;

import java.util.List;
import com.moamap.common.response.ApiResponse;
import com.moamap.map.dto.FootTrafficAreaResponse;
import com.moamap.map.dto.FootTrafficCongestionResponse;
import com.moamap.map.service.FootTrafficQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maps/official/foot-traffic")
@RequiredArgsConstructor
public class FootTrafficController {

    private final FootTrafficQueryService queryService;

    @GetMapping("/areas")
    public ApiResponse<List<FootTrafficAreaResponse>> getAreas() {
        return ApiResponse.success(queryService.findAllAreas());
    }

    @GetMapping("/congestion")
    public ApiResponse<List<FootTrafficCongestionResponse>> getCongestion() {
        return ApiResponse.success(queryService.findAllCongestion());
    }
}
