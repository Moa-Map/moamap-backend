package com.moamap.user.term.catalog;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 약관 본문(.md)이 앱에 안전하게 그려질 수 있는지 검사한다.
 *
 * 앱은 Markdown 렌더러로 그리고 HTML은 실행하지 않기로 했지만, 서버에서도 한 번 더 막는다(이중 방어).
 * 같은 검사를 CI 테스트(TermCatalogFilesTest)와 서버 시작 시에 돌려, 규칙을 어긴 약관은 머지도 배포도 되지 않는다.
 */
final class TermMarkdownPolicy {

    static final int MAX_BYTES = 200 * 1024;

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "mailto", "tel");

    /** HTML 태그·주석·선언. Markdown 자동 링크(<https://...> 등 허용된 scheme)는 제외한다. */
    private static final Pattern HTML = Pattern.compile("<(?!(?:https?|mailto|tel):)[!?/a-zA-Z]", Pattern.CASE_INSENSITIVE);
    /** 이미지. 외부 이미지는 열람 여부를 추적하는 데 쓰일 수 있어 약관에는 두지 않는다. */
    private static final Pattern IMAGE = Pattern.compile("!\\[");
    /** [텍스트](주소) */
    private static final Pattern INLINE_LINK = Pattern.compile("\\]\\(\\s*<?([^\\s)>]*)");
    /** [이름]: 주소 */
    private static final Pattern REFERENCE_LINK = Pattern.compile("(?m)^ {0,3}\\[[^\\]]+\\]:\\s*<?([^\\s>]*)");

    private TermMarkdownPolicy() {
    }

    /** 문제가 없으면 본문을, 있으면 이유를 모아 예외로 돌려준다. */
    static String validate(String file, byte[] bytes) {
        List<String> violations = new ArrayList<>();
        if (bytes.length > MAX_BYTES) {
            violations.add("200KB를 넘습니다(" + bytes.length + " bytes)");
        }
        String content = decodeUtf8(bytes);
        if (content == null) {
            violations.add("UTF-8이 아닙니다");
        } else {
            violations.addAll(contentViolations(content));
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("약관 파일 " + file + "이 규칙을 어깁니다: " + String.join(", ", violations));
        }
        return content;
    }

    static List<String> contentViolations(String content) {
        List<String> violations = new ArrayList<>();
        if (content.isBlank()) {
            violations.add("본문이 비어 있습니다");
        }
        if (HTML.matcher(content).find()) {
            violations.add("HTML 태그는 쓸 수 없습니다");
        }
        if (IMAGE.matcher(content).find()) {
            violations.add("이미지는 쓸 수 없습니다");
        }
        checkLinks(INLINE_LINK.matcher(content), violations);
        checkLinks(REFERENCE_LINK.matcher(content), violations);
        return violations;
    }

    private static void checkLinks(Matcher matcher, List<String> violations) {
        while (matcher.find()) {
            String url = matcher.group(1);
            if (!hasAllowedScheme(url)) {
                violations.add("허용되지 않은 링크입니다(http·https·mailto·tel만 가능): " + url);
            }
        }
    }

    private static boolean hasAllowedScheme(String url) {
        int colon = url.indexOf(':');
        if (colon <= 0) {
            return false; // 상대 경로·빈 주소는 앱에서 엉뚱한 곳으로 열릴 수 있다.
        }
        return ALLOWED_SCHEMES.contains(url.substring(0, colon).toLowerCase(Locale.ROOT));
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return decoded.startsWith("﻿") ? decoded.substring(1) : decoded;
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
