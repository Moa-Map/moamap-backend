package com.moamap.user.auth.exception;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;

public class RefreshTokenNotFoundException extends BusinessException {

    public RefreshTokenNotFoundException(String message) {
        super(UserErrorCode.REFRESH_TOKEN_EXPIRED, message);
    }
}
