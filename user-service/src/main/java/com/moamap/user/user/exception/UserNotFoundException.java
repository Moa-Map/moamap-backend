package com.moamap.user.user.exception;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(String message) {
        super(UserErrorCode.USER_NOT_FOUND, message);
    }
}