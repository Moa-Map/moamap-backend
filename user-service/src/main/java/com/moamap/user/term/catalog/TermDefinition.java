package com.moamap.user.term.catalog;

import com.moamap.user.term.entity.TermKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * terms.yml의 한 줄 = 약관 한 버전.
 *
 * @param code         약관 종류 식별자. 대문자·숫자·밑줄(예: SERVICE)
 * @param title        앱에 보일 제목
 * @param version      버전 표기(예: v1, v1.1)
 * @param kind         REQUIRED(동의 필요) / VIEW_ONLY(보기만). 등록 후 바꾸지 않는다
 * @param active       지금 보여줄 버전인지. 개정하면 이전 버전을 false로 바꾼다
 * @param file         terms/ 아래 본문 파일 이름. 소문자·숫자·하이픈 + .md
 * @param displayOrder 화면 순서(작을수록 위)
 */
public record TermDefinition(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String code,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Pattern(regexp = "v[0-9]{1,3}(\\.[0-9]{1,3})?") String version,
        @NotNull TermKind kind,
        @NotNull Boolean active,
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,90}\\.md") String file,
        @NotNull @PositiveOrZero Integer displayOrder
) {

    String key() {
        return code + "@" + version;
    }
}
