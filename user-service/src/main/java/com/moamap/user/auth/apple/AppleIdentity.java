package com.moamap.user.auth.apple;

/** 서명과 필수 claim 검증이 끝난 Apple 사용자 정보. 이름은 Identity Token에 포함되지 않는다. */
public record AppleIdentity(String subject, String email, String nonce) {
}
