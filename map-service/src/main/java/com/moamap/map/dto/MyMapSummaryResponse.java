package com.moamap.map.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.moamap.map.entity.MapRole;
import com.moamap.map.repository.JoinedMapRow;

/**
 * 모음(내가 참여한 지도) 목록용 지도 요약. 목록 요약에 요청자의 역할(myRole)을 더한다.
 * 요약 필드는 펼쳐서 내려주므로 JSON 모양은 MapSummaryResponse에 myRole이 붙은 형태다.
 */
public record MyMapSummaryResponse(
    @JsonUnwrapped MapSummaryResponse summary,
    MapRole myRole
) {

    public static MyMapSummaryResponse from(JoinedMapRow row) {
        // 내가 참여한 목록이므로 joined는 항상 true.
        return new MyMapSummaryResponse(MapSummaryResponse.of(row.map(), true), row.role());
    }
}
