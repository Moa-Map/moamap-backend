package com.moamap.map.controller;

import java.util.List;
import com.moamap.map.dto.FootTrafficAreaResponse;
import com.moamap.map.dto.FootTrafficCongestionResponse;
import com.moamap.map.service.FootTrafficQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/map/official/foot-traffic")
@RequiredArgsConstructor
public class FootTrafficController {

    private final FootTrafficQueryService queryService;

    @GetMapping("/areas")
    public List<FootTrafficAreaResponse> getAreas() {
        return queryService.findAllAreas();
    }

    @GetMapping("/congestion")
    public List<FootTrafficCongestionResponse> getCongestion() {
        return queryService.findAllCongestion();
    }
}
