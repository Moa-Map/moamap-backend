package com.moamap.user.block.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.user.block.dto.BlockedUserResponse;
import com.moamap.user.block.service.UserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Block", description = "사용자 차단 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserBlockController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserBlockService userBlockService;

    @Operation(summary = "사용자 차단",
            description = "차단하면 그 사용자의 장소·장소 댓글·로그·로그 댓글이 내 목록에서 빠진다(상대는 모른다). "
                    + "운영자에게도 자동으로 알린다. 이미 차단한 사람을 다시 차단해도 성공. "
                    + "자기 자신은 차단할 수 없고(400, USER_026), 탈퇴했거나 없는 사용자는 404.")
    @PostMapping("/{userId}/blocks")
    public ApiResponse<Void> block(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long blockerId,
            @PathVariable Long userId) {
        userBlockService.block(blockerId, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "차단 해제", description = "차단하지 않은 사람을 해제해도 성공.")
    @DeleteMapping("/{userId}/blocks")
    public ApiResponse<Void> unblock(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long blockerId,
            @PathVariable Long userId) {
        userBlockService.unblock(blockerId, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "내 차단 목록", description = "최근 차단한 순. 탈퇴한 사람은 빠진다. 앱은 시작할 때 받아 두고 화면에서도 한 번 더 거른다.")
    @GetMapping("/me/blocks")
    public ApiResponse<List<BlockedUserResponse>> getBlockedUsers(
            @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long blockerId) {
        return ApiResponse.success(userBlockService.getBlockedUsers(blockerId));
    }
}
