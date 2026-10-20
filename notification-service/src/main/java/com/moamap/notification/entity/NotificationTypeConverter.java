package com.moamap.notification.entity;

import jakarta.persistence.Converter;

@Converter
class NotificationTypeConverter extends EnumNameConverter<NotificationType> {

    NotificationTypeConverter() {
        super(NotificationType.class);
    }
}
