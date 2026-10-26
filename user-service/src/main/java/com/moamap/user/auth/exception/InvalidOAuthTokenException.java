package com.moamap.user.auth.exception;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;

public class InvalidOAuthTokenException extends BusinessException {

    public InvalidOAuthTokenException(String message) {
        super(UserErrorCode.INVALID_OAUTH_TOKEN, message);
    }
}
