package com.moamap.map.dto;

import java.util.List;

public record PublicRestroomListResponse(
    List<PublicRestroomMarkerResponse> restrooms,
    boolean truncated
) {
}
