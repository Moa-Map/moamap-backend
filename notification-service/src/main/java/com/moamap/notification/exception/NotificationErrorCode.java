package com.moamap.notification.exception;

import com.moamap.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    // 남의 알림도 같은 응답을 받는다. 다른 사람의 알림 ID가 있는지 알아낼 수 없게 한다.
    NOTIFICATION_NOT_FOUND("NOTIFICATION_001", "알림을 찾을 수 없습니다.", 404);

    private final String code;
    private final String message;
    private final int status;
}
