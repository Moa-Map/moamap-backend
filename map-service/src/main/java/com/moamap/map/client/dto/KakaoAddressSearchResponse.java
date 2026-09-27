package com.moamap.map.client.dto;

import java.util.List;

public record KakaoAddressSearchResponse(List<Document> documents) {
    public record Document(String x, String y) {} // x=lng, y=lat (문자열)
}
