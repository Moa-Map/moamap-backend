package com.moamap.notification.entity;

/**
 * 알림을 눌렀을 때 앱이 열어야 할 대상의 종류. 앱은 이 값과 targetId로 화면을 고른다.
 */
public enum NotificationTargetType {
    PLACE,
    MAP,
    MAP_POST,
    INQUIRY
}
