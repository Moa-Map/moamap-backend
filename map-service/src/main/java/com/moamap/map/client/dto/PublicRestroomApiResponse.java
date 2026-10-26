package com.moamap.map.client.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public record PublicRestroomApiResponse(Response response) {

    public record Response(Header header, Body body) {}

    public record Header(String resultCode, String resultMsg) {}

    public record Body(Items items, Integer numOfRows, Integer pageNo, Integer totalCount) {}

    public record Items(
        // 원천 API는 결과가 1건이면 item을 배열이 아닌 단일 객체로 내려준다.
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<PublicRestroomItem> item
    ) {}
}
