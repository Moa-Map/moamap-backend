package com.moamap.place.mapshare;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공유 텍스트에서 링크만 뽑아낸다.
 *
 * 안드로이드/iOS 공유 인텐트로 들어온 문자열에는 링크 앞뒤로 앱이 붙인 문구가 섞이기 때문에,
 * 사용자가 준 값을 그대로 URL로 취급할 수 없다.
 */
public final class ShareLinkUrlParser {

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+");

    /** URL 끝에 딸려오기 쉬운 문장부호. 링크의 일부가 아니므로 떼어낸다. */
    private static final String TRAILING_PUNCTUATION = ".,;:!?)]}>\"'";

    private ShareLinkUrlParser() {
    }

    /** 텍스트에서 첫 번째 URL을 뽑는다. 없으면 null. */
    public static String firstUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = URL.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return stripTrailingPunctuation(matcher.group());
    }

    private static String stripTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }
}
