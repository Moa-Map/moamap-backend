package com.moamap.user.term.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 약관 성격을 이름 그대로 문자열 컬럼에 저장한다. 저장되는 값은 @Enumerated(EnumType.STRING)과 같다.
 *
 * @Enumerated를 쓰지 않는 이유: Hibernate는 PostgreSQL enum 컬럼에 허용 값 목록을 CHECK 제약으로 만들고,
 * ddl-auto: update는 이미 만든 제약을 고치지 않는다. 나중에 성격(예: 선택 동의)을 추가하면 새 약관 등록이 DB에서 거부된다.
 */
@Converter
class TermKindConverter implements AttributeConverter<TermKind, String> {

    @Override
    public String convertToDatabaseColumn(TermKind attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TermKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TermKind.valueOf(dbData);
    }
}
