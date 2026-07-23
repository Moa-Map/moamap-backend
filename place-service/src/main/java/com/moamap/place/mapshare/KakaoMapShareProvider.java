package com.moamap.place.mapshare;

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
 * 카카오맵 즐겨찾기 폴더 공유 리스트.
 *
 * kko.to 단축링크는 applink.map.kakao.com/open?page=bookmark&folderid=... 로 풀린다.
 * map.kakao.com/?target=other&folderid=... 형태는 folderid가 URL에 그대로 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMapShareProvider implements MapShareProvider {

    /** 주의: folder/info는 folderId(대문자 I), favorite/list는 folderid(소문자 i)다. 카카오 쪽 표기가 실제로 다르다. */
    private static final String INFO_API = "https://map.kakao.com/folder/info?folderId=%s";
    private static final String LIST_API = "https://map.kakao.com/favorite/list?folderid=%s";

    /** Referer가 없으면 카카오맵 API가 403을 반환한다. */
    private static final String REFERER = "https://map.kakao.com/";

    private static final Pattern FOLDER_ID =
        Pattern.compile("[?&]folderid=([0-9]+)", Pattern.CASE_INSENSITIVE);

    private final MapShareHttpClient httpClient;

    @Override
    public PlaceSourceType source() {
        return PlaceSourceType.KAKAO_MAP;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("kko.to")
            || url.contains("map.kakao.com")
            || url.contains("applink.map.kakao.com");
    }

    @Override
    public String resolveListId(String url) {
        String direct = findFolderId(url);
        if (direct != null) {
            return direct;
        }
        String finalUrl = httpClient.resolveFinalUrl(url);
        String found = findFolderId(finalUrl);
        if (found == null) {
            log.warn("카카오 folderid를 찾지 못했습니다. 최종 URL: {}", finalUrl);
            throw new BusinessException(PlaceErrorCode.SHARE_LINK_EXTRACTION_FAILED);
        }
        return found;
    }

    @Override
    public ExtractedList fetchList(String listId) {
        String infoBody = httpClient.getBodyOrThrow(
            String.format(INFO_API, listId), REFERER, "카카오 folder/info");
        String listBody = httpClient.getBodyOrThrow(
            String.format(LIST_API, listId), REFERER, "카카오 favorite/list");
        return parse(infoBody, listBody, listId);
    }

    /** URL에서 folderid 값을 뽑는다. 대소문자를 가리지 않는다. 없으면 null. */
    static String findFolderId(String text) {
        Matcher matcher = FOLDER_ID.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** folder/info와 favorite/list 응답을 합쳐 정규화한다. */
    static ExtractedList parse(String infoBody, String listBody, String listId) {
        JsonNode folder = NaverMapShareProvider.readTree(infoBody).path("folders").path(0);

        List<ExtractedMapPlace> places = new ArrayList<>();
        for (JsonNode favorite : NaverMapShareProvider.readTree(listBody).path("favorites")) {
            places.add(new ExtractedMapPlace(
                JsonNodes.text(favorite.path("display1")),
                JsonNodes.text(favorite.path("display2")),
                null,                                        // 카카오 리스트 응답에는 분류가 없다
                JsonNodes.decimal(favorite.path("lat")),
                JsonNodes.decimal(favorite.path("lon")),
                JsonNodes.text(favorite.path("key")),
                JsonNodes.text(favorite.path("memo"))));
        }

        return new ExtractedList(
            PlaceSourceType.KAKAO_MAP,
            listId,
            JsonNodes.text(folder.path("title")),
            JsonNodes.text(folder.path("nickname")),
            JsonNodes.integer(folder.path("favorite_cnt")),
            places);
    }
}
