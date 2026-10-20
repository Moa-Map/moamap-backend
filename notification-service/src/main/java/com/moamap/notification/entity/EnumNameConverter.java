package com.moamap.notification.entity;

import jakarta.persistence.AttributeConverter;

/**
 * enum을 이름 그대로 문자열 컬럼에 저장한다. 저장되는 값은 @Enumerated(EnumType.STRING)과 같다.
 *
 * @Enumerated를 쓰지 않는 이유: Hibernate는 enum 컬럼에 허용 값 목록을 CHECK 제약으로 박는데,
 * ddl-auto: update는 이미 만든 제약을 고치지 않는다. 알림 종류를 추가하면 새 알림 저장이 DB에서 거부된다.
 * 컨버터를 거치면 DB에는 평범한 문자열 컬럼으로 만들어지고, 허용 값 검증은 애플리케이션의 enum이 맡는다.
 */
abstract class EnumNameConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected EnumNameConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumType, dbData);
    }
}
