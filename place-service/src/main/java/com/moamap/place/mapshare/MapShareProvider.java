package com.moamap.place.mapshare;

import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.mapshare.dto.ExtractedList;

/**
 * 지도 서비스 한 곳의 공개 저장 리스트를 읽어오는 추출기.
 *
 * resolveListId와 fetchList를 나눈 이유는 캐싱 이음매를 미리 만들어 두기 위함이다.
 * 단축코드는 공유할 때마다 새로 발급되지만 안쪽 ID(shareId/folderid/listId)는 고정이라,
 * 나중에 캐시를 넣는다면 반드시 URL이 아니라 ID를 키로 삼아야 한다.
 * 지금은 캐시를 구현하지 않는다 - 재추출 비율을 로그로 측정한 뒤 판단한다.
 */
public interface MapShareProvider {

    /** 이 Provider가 만들어내는 장소의 출처 유형. */
    PlaceSourceType source();

    /** 이 URL을 처리할 수 있는가. 호스트만 보고 판단한다. */
    boolean supports(String url);

    /** 입력 URL(단축링크 포함)에서 리스트 ID를 얻는다. 못 찾으면 예외를 던진다. */
    String resolveListId(String url);

    /** 리스트 ID로 장소 목록을 가져온다. */
    ExtractedList fetchList(String listId);
}
