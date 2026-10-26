package com.moamap.map.dto;

import java.util.List;
import com.moamap.map.entity.MapType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 모음 탭 순서 변경 요청. 해당 타입에서 내가 참여 중인 지도 전체를 원하는 순서대로 담는다.
 *
 * 부분 이동("3번을 1번 자리로")이 아니라 전체 목록 교체인 이유는 두 가지다.
 * 같은 요청을 여러 번 보내도 결과가 같고(멱등), 요청이 겹쳐도 순서가 어중간하게 섞이지 않는다.
 */
public record MapOrderUpdateRequest(

    @Schema(description = "순서를 바꿀 탭", example = "PRIVATE")
    @NotNull(message = "지도 유형은 필수입니다.")
    MapType type,

    @Schema(description = "원하는 순서대로 나열한 지도 ID. 해당 탭에서 내가 참여 중인 지도 전체를 빠짐없이 담는다.")
    @NotEmpty(message = "지도 목록은 비어 있을 수 없습니다.")
    @Size(max = MapOrderUpdateRequest.MAX_MAPS, message = "한 번에 정렬할 수 있는 지도는 최대 "
        + MapOrderUpdateRequest.MAX_MAPS + "개입니다.")
    List<@NotNull(message = "지도 ID는 비어 있을 수 없습니다.") Long> mapIds
) {

    /** 요청 본문 크기를 묶어두는 상한. 한 사용자가 이보다 많은 지도에 참여할 일은 없다고 본다. */
    public static final int MAX_MAPS = 300;
}
