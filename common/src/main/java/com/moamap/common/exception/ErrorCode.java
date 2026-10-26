package com.moamap.common.exception;

/**
 * 서비스별 에러 코드 enum이 구현해야 하는 인터페이스.
 * status는 HTTP 상태 코드 값(int)으로 둬서 common 모듈이 spring-web에 의존하지 않게 한다.
 */
public interface ErrorCode {

    String getCode();

    String getMessage();

    int getStatus();
}
