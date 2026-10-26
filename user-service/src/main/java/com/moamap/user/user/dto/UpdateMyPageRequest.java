package com.moamap.user.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMyPageRequest(
        @Pattern(regexp = "\\S.*", message = "닉네임은 공백일 수 없습니다.")
        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
        String nickname,
        String profileImageUrl,
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,
        String introduction
) {
}
