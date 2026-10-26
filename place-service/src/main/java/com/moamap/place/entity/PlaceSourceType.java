package com.moamap.place.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlaceSourceType {
    KAKAO_SEARCH(false),
    INSTAGRAM(true),
    NAVER_MAP(true),
    KAKAO_MAP(true),
    GOOGLE_MAP(true);

    /** 링크를 붙여넣어 가져온 출처인지. 지도의 'URL로 장소 추가 허용' 설정이 적용된다. */
    private final boolean fromUrl;
}
