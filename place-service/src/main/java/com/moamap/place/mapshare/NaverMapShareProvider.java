package com.moamap.place.mapshare;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.mapshare.dto.ExtractedList;
import com.moamap.place.mapshare.dto.ExtractedMapPlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 네이버지도 즐겨찾기(저장) 공유 리스트.
 *
 * naver.me 단축링크를 따라가 최종 URL에서 32자리 hex shareId를 뽑고,
 * 로그인 없이 열리는 공개 bookmarks API를 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverMapShareProvider implements MapShareProvider {

    private static final String BOOKMARKS_API =
        "https://pages.map.naver.com/save-pages/api/maps-bookmark/v3/shares/%s/bookmarks";

    private static final String DETAIL_LIST_URL =
        "https://pages.map.naver.com/save-pages/web/detail-list/%s";

    /**
     * 최종 URL 안의 shareId(32자리 hex).
     *  - ?id=189963...                                      (appLink.naver)
     *  - detail-list/189963... 또는 detail-list%2F189963...  (fallbackUrl)
     */
    private static final Pattern SHARE_ID =
        Pattern.compile("(?:[?&]id=|detail-list(?:/|%2F))([0-9a-fA-F]{32})");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MapShareHttpClient httpClient;

    @Override
    public PlaceSourceType source() {
        return PlaceSourceType.NAVER_MAP;
    }

    @Override
    public boolean supports(String url) {
        return ShareLinkUrlParser.hostMatches(url, "naver.me", "map.naver.com");
    }

    @Override
    public String resolveListId(String url) {
        String direct = findShareId(url);
        if (direct != null) {
            return direct;
        }
        String finalUrl = httpClient.resolveFinalUrl(url);
        String found = findShareId(finalUrl);
        if (found == null) {
            // fallbackUrl이 URL 인코딩된 경우까지 커버한다.
            found = findShareId(URLDecoder.decode(finalUrl, StandardCharsets.UTF_8));
        }
        if (found == null) {
            log.warn("네이버 shareId를 찾지 못했습니다. 최종 URL: {}", finalUrl);
            throw new BusinessException(PlaceErrorCode.SHARE_LINK_EXTRACTION_FAILED);
        }
        return found;
    }

    @Override
    public ExtractedList fetchList(String listId) {
        String body = httpClient.getBodyOrThrow(
            String.format(BOOKMARKS_API, listId),
            String.format(DETAIL_LIST_URL, listId),
            "네이버 bookmarks API");
        return parse(body, listId);
    }

    /** URL에서 shareId(32자리 hex)를 뽑아 소문자로 정규화한다. 없으면 null. */
    static String findShareId(String text) {
        Matcher matcher = SHARE_ID.matcher(text);
        return matcher.find() ? matcher.group(1).toLowerCase() : null;
    }

    /** bookmarks API 응답을 정규화한다. HTTP를 타지 않으므로 픽스처로 테스트할 수 있다. */
    static ExtractedList parse(String body, String listId) {
        JsonNode root = readTree(body);
        JsonNode folder = root.path("folder");

        List<ExtractedMapPlace> places = new ArrayList<>();
        for (JsonNode bookmark : root.path("bookmarkList")) {
            places.add(new ExtractedMapPlace(
                JsonNodes.text(bookmark.path("name")),
                JsonNodes.text(bookmark.path("address")),
                JsonNodes.text(bookmark.path("mcidName")),
                JsonNodes.decimal(bookmark.path("py")),   // py = 위도
                JsonNodes.decimal(bookmark.path("px")),   // px = 경도
                JsonNodes.text(bookmark.path("sid")),
                JsonNodes.text(bookmark.path("memo"))));
        }

        // 작성자 닉네임(placeUserProfile.nick)은 네이버가 마스킹해서 내려준다("wk****").
        return new ExtractedList(
            PlaceSourceType.NAVER_MAP,
            listId,
            JsonNodes.text(folder.path("name")),
            null,
            JsonNodes.integer(folder.path("bookmarkCount")),
            places);
    }

    static JsonNode readTree(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            log.warn("지도 공유 리스트 응답을 JSON으로 파싱하지 못했습니다.", e);
            throw new BusinessException(PlaceErrorCode.SHARE_LIST_STRUCTURE_CHANGED);
        }
    }
}
