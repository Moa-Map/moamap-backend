package com.moamap.user.auth.apple;

public record AppleNonceResponse(String nonce, long expiresIn) {
}
