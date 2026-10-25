package com.moamap.map.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 신고 관련 enum을 이름 그대로 문자열 컬럼에 저장한다. 저장되는 값은 @Enumerated(EnumType.STRING)과 같다.
 *
 * @Enumerated를 쓰면 PostgreSQL에 허용 값 CHECK 제약이 생기고, ddl-auto: update는 그 제약을 고치지 않는다.
 * 사유·대상을 추가할 때 저장이 거부되지 않도록 컨버터로 평범한 문자열 컬럼을 만든다.
 */
final class EnumNameConverters {

    private EnumNameConverters() {
    }

    @Converter
    static class ReportReasonConverter implements AttributeConverter<ReportReason, String> {

        @Override
        public String convertToDatabaseColumn(ReportReason attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public ReportReason convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReportReason.valueOf(dbData);
        }
    }

    @Converter
    static class ReportTargetTypeConverter implements AttributeConverter<ReportTargetType, String> {

        @Override
        public String convertToDatabaseColumn(ReportTargetType attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public ReportTargetType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReportTargetType.valueOf(dbData);
        }
    }
}
