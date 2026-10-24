package com.moamap.map.exception;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * map-service 공통 예외 처리. 모든 응답을 ApiResponse 포맷으로 통일한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .orElse(CommonErrorCode.INVALID_INPUT_VALUE.getMessage());
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
            .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, message));
    }

    /** @RequestParam·@PathVariable에 붙은 제약(@NotBlank 등) 위반. 없으면 handleUnexpected로 흘러 500이 된다. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
            .findFirst()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .orElse(CommonErrorCode.INVALID_INPUT_VALUE.getMessage());
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
            .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, message));
    }

    /**
     * JSON 형식이 깨졌거나 enum에 없는 값(예: 신고 사유)을 보낸 경우. 처리하지 않으면 500으로 떨어진다.
     * 원인 메시지에는 내부 클래스명이 섞이므로 그대로 내보내지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
            .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, "요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
            .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, "필수 파라미터(" + e.getParameterName() + ")가 필요합니다."));
    }

    /** 파라미터 값의 형식이 틀린 경우(예: 신고 대상 종류에 없는 값). 처리하지 않으면 500으로 떨어진다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
            .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(CommonErrorCode.UNAUTHORIZED.getStatus())
            .body(ApiResponse.error(CommonErrorCode.UNAUTHORIZED, "인증 정보(" + e.getHeaderName() + ")가 필요합니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        // 내부 상세는 로그로만 남기고 클라이언트에는 일반화된 메시지만 반환한다.
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
            .body(ApiResponse.error(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
}
