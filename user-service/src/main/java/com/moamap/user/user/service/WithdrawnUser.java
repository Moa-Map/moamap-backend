package com.moamap.user.user.service;

/**
 * 탈퇴 트랜잭션이 끝난 뒤 외부 정리에 필요한 값. 익명화로 DB에서는 이미 지워진 값들이다.
 *
 * Apple 토큰은 비밀값이라 로그에 찍히지 않도록 toString을 가린다.
 */
record WithdrawnUser(Long userId, String profileImageUrl, String appleRefreshToken) {

    @Override
    public String toString() {
        return "WithdrawnUser[userId=" + userId + ", redacted]";
    }
}
