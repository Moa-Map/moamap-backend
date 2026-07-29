package com.moamap.user.user.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.user.dto.ProfileUploadUrlRequest;
import com.moamap.user.user.dto.ProfileUploadUrlResponse;
import com.moamap.user.user.service.UserProfilePhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "UserProfilePhoto", description = "유저 프로필 이미지 업로드 API")
@RestController
@RequestMapping("/api/v1/users/profile-upload-url")
@RequiredArgsConstructor
public class UserProfilePhotoController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserProfilePhotoService userProfilePhotoService;

    @Operation(
        summary = "프로필 이미지 업로드 URL 발급",
        description = """
            프로필 이미지를 올릴 presigned PUT URL을 발급한다. 이미지는 1장만 허용한다.
            발급받은 uploadUrl로 파일을 직접 PUT한 뒤, 같은 응답의 fileUrl을 마이페이지 수정 요청에 담아 전달한다.
            허용 형식은 image/jpeg, image/png, image/webp이며 최대 10MB까지 발급한다.
            """
    )
    @PostMapping
    public ApiResponse<ProfileUploadUrlResponse> issueProfileUploadUrl(
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
        @Valid @RequestBody ProfileUploadUrlRequest request
    ) {
        return ApiResponse.success(userProfilePhotoService.issueUploadUrl(request, userId));
    }
}
