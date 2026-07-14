package com.moamap.common.response;

import com.moamap.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 에러 응답 포맷.
 */
@Getter
public class ErrorResponse {

    private final String code;
    private final String message;
    private final int status;

    private ErrorResponse(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errorCode.getStatus());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, errorCode.getStatus());
    }
}
