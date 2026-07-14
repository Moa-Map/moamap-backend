package com.moamap.map.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 프라이빗 지도 초대 코드 생성기.
 * 사람이 옮겨 적기 쉽도록 헷갈리는 문자(0/O, 1/I/L)를 제외한 알파벳/숫자만 사용한다.
 */
@Component
public class InviteCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
