package com.moamap.user.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 신고 사유를 이름 그대로 문자열 컬럼에 저장한다. @Enumerated를 쓰면 PostgreSQL에 허용 값 CHECK 제약이 생기고,
 * ddl-auto: update는 그 제약을 고치지 않아 사유를 추가할 때 저장이 거부된다(TermKindConverter와 같은 이유).
 */
@Converter
class ReportReasonConverter implements AttributeConverter<ReportReason, String> {

    @Override
    public String convertToDatabaseColumn(ReportReason attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public ReportReason convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReportReason.valueOf(dbData);
    }
}
