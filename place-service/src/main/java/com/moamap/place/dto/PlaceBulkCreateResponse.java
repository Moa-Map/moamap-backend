package com.moamap.place.dto;

import java.util.List;

/**
 * 장소 일괄 등록 결과. 건별 부분 성공이라 실패한 건도 사유와 함께 돌려준다.
 */
public record PlaceBulkCreateResponse(
    int requested,
    int created,
    int skipped,
    List<Result> results
) {

    public enum Status {
        CREATED,
        DUPLICATE,
        FAILED
    }

    public record Result(int index, String name, Status status, Long placeId, String reason) {

        public static Result created(int index, String name, Long placeId) {
            return new Result(index, name, Status.CREATED, placeId, null);
        }

        public static Result duplicate(int index, String name, String reason) {
            return new Result(index, name, Status.DUPLICATE, null, reason);
        }

        public static Result failed(int index, String name, String reason) {
            return new Result(index, name, Status.FAILED, null, reason);
        }
    }
}
