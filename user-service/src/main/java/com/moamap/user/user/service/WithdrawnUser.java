package com.moamap.user.user.service;

/**
 * 탈퇴 트랜잭션이 끝난 뒤 외부 정리에 필요한 값. Apple 토큰은 DB에 암호화된 채 폐기 대기로 남아 있다.
 */
record WithdrawnUser(Long userId, boolean appleRevokePending) {
}
