package com.moamap.map.service;

import java.util.List;
import com.moamap.map.dto.FootTrafficAreaResponse;
import com.moamap.map.dto.FootTrafficCongestionResponse;
import com.moamap.map.repository.FootTrafficAreaRepository;
import com.moamap.map.repository.FootTrafficCongestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FootTrafficQueryService {

    private final FootTrafficAreaRepository footTrafficAreaRepository;
    private final FootTrafficCongestionRepository congestionRepository;

    public List<FootTrafficAreaResponse> findAllAreas() {
        return footTrafficAreaRepository.findAll().stream()
            .map(FootTrafficAreaResponse::from)
            .toList();
    }

    public List<FootTrafficCongestionResponse> findAllCongestion() {
        return congestionRepository.findAll().stream()
            .map(FootTrafficCongestionResponse::from)
            .toList();
    }
}
