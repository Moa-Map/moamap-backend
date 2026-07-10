package com.moamap.user.auth.exception;

public class InvalidOAuthTokenException extends RuntimeException {

    public InvalidOAuthTokenException(String message) {
        super(message);
    }
}
