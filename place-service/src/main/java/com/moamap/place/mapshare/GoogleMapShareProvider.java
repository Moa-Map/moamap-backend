package com.moamap.place.mapshare;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.mapshare.dto.ExtractedList;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 구글맵 저장 목록(placelist) 공유 리스트.
 *
 * maps.app.goo.gl 단축링크는 google.com/maps/@/data=!4m3!11m2!2s{listId}!3e3... 로 풀린다.
 *
 * 응답은 ")]}'" 프리픽스가 붙은 키 없는 중첩 배열이다. 인덱스 경로:
 *   [0][4]              리스트 이름
 *   [0][3][0]           작성자
 *   [0][12]             선언된 장소 수
 *   [0][8]              장소 배열
 *   [0][8][i][2]        장소명
 *   [0][8][i][3]        메모
 *   [0][8][i][1][4]     주소
 *   [0][8][i][1][5][2]  위도
 *   [0][8][i][1][5][3]  경도
 *   [0][8][i][1][6][1]  CID (쌍의 두 번째 값)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleMapShareProvider implements MapShareProvider {

    private static final String LIST_API =
        "https://www.google.com/maps/preview/entitylist/getlist?hl=ko&gl=kr&pb=";

    /** 4i500 = 최대 500개까지 요청. */
    private static final String PB_TEMPLATE = "!1m4!1s%s!2e1!3m1!1e1!2e2!3e2!4i500";

    /** data=... 안의 !2s{listId}. listId는 base64url 계열 문자로만 이루어진다. */
    private static final Pattern LIST_ID = Pattern.compile("!2s([A-Za-z0-9_-]{20,})");

    /** XSSI 방어용 프리픽스. */
    private static final String XSSI_PREFIX = ")]}'";

    private final MapShareHttpClient httpClient;

    @Override
    public PlaceSourceType source() {
        return PlaceSourceType.GOOGLE_MAP;
    }

    @Override
    public boolean supports(String url) {
        // maps.app.goo.gl은 goo.gl 서브도메인이라 goo.gl로 함께 매칭된다.
        return ShareLinkUrlParser.hostMatches(url, "goo.gl", "google.com");
    }

    @Override
    public String resolveListId(String url) {
        String direct = findListId(url);
        if (direct != null) {
            return direct;
        }
        String finalUrl = httpClient.resolveFinalUrl(url);
        String found = findListId(finalUrl);
        if (found == null) {
            log.warn("구글 listId를 찾지 못했습니다. 최종 URL: {}", finalUrl);
            throw new BusinessException(PlaceErrorCode.SHARE_LINK_EXTRACTION_FAILED);
        }
        return found;
    }

    @Override
    public ExtractedList fetchList(String listId) {
        String pb = URLEncoder.encode(String.format(PB_TEMPLATE, listId), StandardCharsets.UTF_8);
        String body = httpClient.getBodyOrThrow(LIST_API + pb, null, "구글 entitylist API");
        return parse(body, listId);
    }

    /** URL에서 !2s 뒤의 listId를 뽑는다. 없으면 null. */
    static String findListId(String text) {
        Matcher matcher = LIST_ID.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 구글 응답 앞에 붙는 XSSI 방어용 프리픽스를 제거한다. */
    static String stripPrefix(String body) {
        return body.startsWith(XSSI_PREFIX) ? body.substring(XSSI_PREFIX.length()) : body;
    }

    /** entitylist 응답을 정규화한다. */
    static ExtractedList parse(String body, String listId) {
        JsonNode root = NaverMapShareProvider.readTree(stripPrefix(body)).path(0);

        List<ExtractedMapPlace> places = new ArrayList<>();
        for (JsonNode entry : root.path(8)) {
            JsonNode detail = entry.path(1);
            places.add(new ExtractedMapPlace(
                JsonNodes.text(entry.path(2)),
                JsonNodes.text(detail.path(4)),
                null,                                     // 구글 리스트 응답에는 분류가 없다
                JsonNodes.decimal(detail.path(5).path(2)),
                JsonNodes.decimal(detail.path(5).path(3)),
                JsonNodes.text(detail.path(6).path(1)),   // 쌍의 두 번째 값이 CID
                JsonNodes.text(entry.path(3))));
        }

        return new ExtractedList(
            PlaceSourceType.GOOGLE_MAP,
            listId,
            JsonNodes.text(root.path(4)),
            JsonNodes.text(root.path(3).path(0)),
            JsonNodes.integer(root.path(12)),
            places);
    }
}
