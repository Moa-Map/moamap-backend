package com.moamap.place.dto;

/**
 * 추출된 장소를 카카오 장소와 매칭하지 못한 이유.
 * 사유를 나눠야 프론트에서 다르게 안내할 수 있고, 임계값이 너무 빡빡한지 로그로 판단할 수 있다.
 */
public enum UnmatchReason {

    /** 카카오 검색 결과가 0건 */
    NO_RESULT,

    /** 결과는 있으나 이름이 맞는 것이 없음 */
    NAME_MISMATCH,

    /** 이름은 맞으나 전부 임계 거리를 넘음 */
    TOO_FAR,

    /** 카카오 검색 호출 자체가 실패 */
    SEARCH_FAILED
}
