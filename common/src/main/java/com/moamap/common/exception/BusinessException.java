package com.moamap.common.exception;

import lombok.Getter;

/**
 * 도메인/비즈니스 규칙 위반을 나타내는 공통 예외.
 * 각 서비스는 이 예외를 그대로 쓰거나 상속해서 사용한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
