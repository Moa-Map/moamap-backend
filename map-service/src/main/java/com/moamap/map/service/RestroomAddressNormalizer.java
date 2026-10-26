package com.moamap.map.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 카카오 지오코딩 호출 직전 주소를 정리한다. 원천 주소에는 지도앱에서만 쓰는 부가 설명(건물 부기, 상세 위치)이
 * 섞여 있어 그대로 보내면 카카오 검색이 실패하는 경우가 있다 (청사진 3-1 (E)).
 */
public final class RestroomAddressNormalizer {

    private static final Pattern LOT_ADDRESS_PATTERN =
        Pattern.compile("^(.*?\\s(?:산\\s?)?\\d+(?:-\\d+)?)(?=\\s|$)");

    private RestroomAddressNormalizer() {
    }

    public static String normalizeRoadAddress(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        int commaIndex = address.indexOf(',');
        int parenIndex = address.indexOf(" (");
        int cutIndex = firstNonNegative(commaIndex, parenIndex);
        if (cutIndex < 0) {
            return address;
        }
        return address.substring(0, cutIndex).trim();
    }

    public static String normalizeLotAddress(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        Matcher matcher = LOT_ADDRESS_PATTERN.matcher(address);
        if (!matcher.find()) {
            return address;
        }
        return matcher.group(1);
    }

    private static int firstNonNegative(int a, int b) {
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }
}
