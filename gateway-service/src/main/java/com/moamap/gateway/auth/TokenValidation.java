package com.moamap.gateway.auth;

/**
 * 토큰 검증 결과. 만료(EXPIRED)와 그 외 무효(INVALID)를 구분해서,
 * 만료일 때만 앱이 리프레시로 자동 갱신할 수 있게 한다.
 */
public record TokenValidation(Status status, Long userId) {

    public enum Status {
        VALID, EXPIRED, INVALID
    }

    public static TokenValidation valid(Long userId) {
        return new TokenValidation(Status.VALID, userId);
    }

    public static TokenValidation expired() {
        return new TokenValidation(Status.EXPIRED, null);
    }

    public static TokenValidation invalid() {
        return new TokenValidation(Status.INVALID, null);
    }

    public boolean isValid() {
        return status == Status.VALID;
    }
}
