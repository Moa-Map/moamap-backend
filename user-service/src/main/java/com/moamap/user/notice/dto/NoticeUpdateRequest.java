package com.moamap.user.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 부분 수정. 보낸 값만 바꾸고 빠진 값은 그대로 둔다. 보낸 값은 비어 있으면 안 된다.
 */
public record NoticeUpdateRequest(

        @Schema(description = "바꿀 제목. 보내지 않으면 그대로 둔다.", example = "서비스 점검 안내 (시간 변경)")
        @Pattern(regexp = "(?s).*\\S.*", message = "제목은 비워둘 수 없습니다.")
        @Size(max = NoticeCreateRequest.MAX_TITLE, message = "제목은 100자 이하여야 합니다.")
        String title,

        @Schema(description = "바꿀 본문. 보내지 않으면 그대로 둔다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "본문은 비워둘 수 없습니다.")
        @Size(max = NoticeCreateRequest.MAX_CONTENT, message = "본문은 5000자 이하여야 합니다.")
        String content
) {

    @Schema(hidden = true)
    @AssertTrue(message = "수정할 제목이나 본문을 하나 이상 보내야 합니다.")
    public boolean isAnyFieldPresent() {
        return title != null || content != null;
    }
}
