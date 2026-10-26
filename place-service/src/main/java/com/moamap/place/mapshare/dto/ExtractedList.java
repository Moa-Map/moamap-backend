package com.moamap.place.mapshare.dto;

import java.util.List;
import com.moamap.place.entity.PlaceSourceType;

/**
 * 추출된 리스트 전체.
 *
 * declaredCount는 서비스가 알려주는 "리스트에 있어야 할 장소 수"다
 * (네이버 folder.bookmarkCount, 카카오 folders[0].favorite_cnt, 구글 [0][12]).
 * places.size()와 비교해 응답 구조 변경을 조기에 잡는 데 쓴다. 없으면 null.
 */
public record ExtractedList(
    PlaceSourceType source,
    String listId,
    String listName,
    String owner,
    Integer declaredCount,
    List<ExtractedMapPlace> places
) {
}
