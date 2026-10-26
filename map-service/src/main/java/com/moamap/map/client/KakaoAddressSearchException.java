package com.moamap.map.client;

/** 429 외 실패(5xx/4xx/타임아웃 등) — sync 서비스는 해당 건만 스킵하고 계속 진행한다. */
public class KakaoAddressSearchException extends RuntimeException {

    public KakaoAddressSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
