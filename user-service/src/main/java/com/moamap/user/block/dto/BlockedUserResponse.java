package com.moamap.user.block.dto;

import java.time.Instant;

/** 차단 목록의 한 사람. 설정 화면에서 보여주고 해제할 때 쓴다. */
public record BlockedUserResponse(Long userId, String nickname, String profileImageUrl, Instant blockedAt) {
}
