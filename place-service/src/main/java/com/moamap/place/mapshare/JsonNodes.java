package com.moamap.place.mapshare;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JsonNode 안전 접근 헬퍼.
 *
 * path()는 없는 경로에 MissingNode를 주지만 asText()가 ""를, decimalValue()가 0을
 * 반환해 "없음"과 "빈 값"이 섞인다. 여기서 전부 null로 정규화한다.
 */
final class JsonNodes {

    private JsonNodes() {
    }

    /** 문자열 값. 없거나 null이거나 공백뿐이면 null. */
    static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    /** 숫자 값. 숫자가 아니면 null. */
    static BigDecimal decimal(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }

    /** 정수 값. 숫자가 아니면 null. */
    static Integer integer(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }
}
