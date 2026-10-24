package com.moamap.map.internal;

import java.util.List;
import com.moamap.common.response.ApiResponse;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.repository.MapMemberRepository;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스만 부르는 내부 API. 게이트웨이는 /internal 경로를 연결하지 않으므로 외부에서는 닿지 않는다.
 */
@Hidden
@RestController
@RequestMapping("/internal/maps")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalMapController {

    private final MapMemberRepository mapMemberRepository;

    /** 지도의 방장·관리자 ID. 장소 신고 알림을 받을 사람이다. 없는 지도면 빈 목록이다. */
    @GetMapping("/{mapId}/managers")
    public ApiResponse<List<Long>> managers(@PathVariable Long mapId) {
        return ApiResponse.success(mapMemberRepository.findByMapId(mapId).stream()
            .filter(member -> member.getRole() == MapRole.OWNER || member.getRole() == MapRole.ADMIN)
            .map(MapMember::getUserId)
            .toList());
    }
}
