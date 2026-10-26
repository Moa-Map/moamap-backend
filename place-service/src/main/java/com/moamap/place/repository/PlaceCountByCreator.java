package com.moamap.place.repository;

/**
 * 멤버별 등록 장소 수 집계 결과 한 줄. 멤버 관리 화면의 "등록한 장소 수"에 쓰인다.
 *
 * 등록 이력이 없는 멤버는 GROUP BY 결과에 아예 나오지 않으므로, 0으로 채우는 일은 호출부가 맡는다.
 */
public interface PlaceCountByCreator {

    Long getCreatedBy();

    long getPlaceCount();
}
