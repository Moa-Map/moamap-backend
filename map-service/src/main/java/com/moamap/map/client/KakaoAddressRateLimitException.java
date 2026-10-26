package com.moamap.map.client;

/** HTTP 429 전용 — sync 서비스가 이 예외일 때만 지오코딩 루프 전체를 중단한다. */
public class KakaoAddressRateLimitException extends RuntimeException {

    public KakaoAddressRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
