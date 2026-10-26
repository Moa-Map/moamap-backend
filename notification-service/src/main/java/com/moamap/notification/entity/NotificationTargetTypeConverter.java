package com.moamap.notification.entity;

import jakarta.persistence.Converter;

@Converter
class NotificationTargetTypeConverter extends EnumNameConverter<NotificationTargetType> {

    NotificationTargetTypeConverter() {
        super(NotificationTargetType.class);
    }
}
