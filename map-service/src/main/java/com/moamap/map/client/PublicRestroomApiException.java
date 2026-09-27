package com.moamap.map.client;

/** resultCode != "0" 또는 HTTP/파싱 실패를 이 예외 하나로 통일해서 던진다. sync 서비스는 이 예외만 잡으면 "이 페이지는 실패했다"를 판단할 수 있다. */
public class PublicRestroomApiException extends RuntimeException {

    public PublicRestroomApiException(String message) {
        super(message);
    }

    public PublicRestroomApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
