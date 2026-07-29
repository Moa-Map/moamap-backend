package com.moamap.map.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.map.dto.JoinByInviteCodeRequest;
import com.moamap.map.dto.MapCreateRequest;
import com.moamap.map.dto.MapDetailResponse;
import com.moamap.map.dto.MapMemberListResponse;
import com.moamap.map.dto.MapMemberRoleResponse;
import com.moamap.map.dto.MapMemberRoleUpdateRequest;
import com.moamap.map.dto.MapMemberRoleUpdateResponse;
import com.moamap.map.dto.MapSort;
import com.moamap.map.dto.MapSummaryResponse;
import com.moamap.map.dto.MapUpdateRequest;
import com.moamap.map.dto.PageResponse;
import com.moamap.map.entity.MapType;
import com.moamap.map.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Map", description = "지도 방 생성/조회/참여 API")
@RestController
@RequestMapping("/api/v1/maps")
@RequiredArgsConstructor
public class MapController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final MapService mapService;

    @Operation(summary = "지도 생성", description = "공개(COMMUNITY) 또는 프라이빗 지도를 만든다. 프라이빗은 초대 코드가 발급된다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MapDetailResponse> create(
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId,
        @Valid @RequestBody MapCreateRequest request
    ) {
        return ApiResponse.success(mapService.create(request, userId));
    }

    @Operation(summary = "커뮤니티 지도 목록", description = "공개 지도를 태그/정렬 조건으로 조회한다.")
    @GetMapping
    public ApiResponse<PageResponse<MapSummaryResponse>> getCommunityMaps(
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) MapSort sort,
        @PageableDefault(size = 20) Pageable pageable,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(PageResponse.from(mapService.getCommunityMaps(tag, sort, pageable, userId)));
    }

    @Operation(summary = "내가 참여한 지도 목록", description = "모음 화면. type으로 커뮤니티/프라이빗을 구분한다.")
    @GetMapping("/me")
    public ApiResponse<PageResponse<MapSummaryResponse>> getMyMaps(
        @RequestParam MapType type,
        @PageableDefault(size = 20) Pageable pageable,
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId
    ) {
        return ApiResponse.success(PageResponse.from(mapService.getMyMaps(type, pageable, userId)));
    }

    @Operation(summary = "지도 상세 조회", description = "프라이빗 지도는 멤버만 조회할 수 있다.")
    @GetMapping("/{mapId}")
    public ApiResponse<MapDetailResponse> getDetail(
        @PathVariable Long mapId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(mapService.getDetail(mapId, userId));
    }

    @Operation(summary = "지도 수정", description = "OWNER 또는 ADMIN만 수정할 수 있다.")
    @PatchMapping("/{mapId}")
    public ApiResponse<MapDetailResponse> update(
        @PathVariable Long mapId,
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId,
        @Valid @RequestBody MapUpdateRequest request
    ) {
        return ApiResponse.success(mapService.update(mapId, userId, request));
    }

    @Operation(summary = "지도 삭제", description = "OWNER만 삭제할 수 있다.")
    @DeleteMapping("/{mapId}")
    public ApiResponse<Void> delete(
        @PathVariable Long mapId,
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId
    ) {
        mapService.delete(mapId, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "커뮤니티 지도 참여", description = "공개 지도에 바로 참여한다.")
    @PostMapping("/{mapId}/join")
    public ApiResponse<MapDetailResponse> joinCommunity(
        @PathVariable Long mapId,
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId
    ) {
        return ApiResponse.success(mapService.joinCommunity(mapId, userId));
    }

    @Operation(summary = "초대 코드로 지도 합류", description = "프라이빗 지도에 초대 코드로 합류한다.")
    @PostMapping("/join")
    public ApiResponse<MapDetailResponse> joinByInviteCode(
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId,
        @Valid @RequestBody JoinByInviteCodeRequest request
    ) {
        return ApiResponse.success(mapService.joinByInviteCode(request.inviteCode(), userId));
    }

    @Operation(summary = "지도 나가기", description = "참여 중인 지도에서 나간다. OWNER는 나갈 수 없다.")
    @DeleteMapping("/{mapId}/members/me")
    public ApiResponse<Void> leave(
        @PathVariable Long mapId,
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId
    ) {
        mapService.leave(mapId, userId);
        return ApiResponse.success();
    }

    @Operation(
        summary = "지도 멤버 목록 조회",
        description = "지도에 참여 중인 멤버를 역할 순(방장 → 관리자 → 멤버)으로 조회한다. 해당 지도의 멤버만 조회할 수 있다. "
            + "닉네임과 프로필 이미지는 user-service에서 가져오며, 조회에 실패하면 해당 값만 비운 채 목록을 반환한다."
    )
    @GetMapping("/{mapId}/members")
    public ApiResponse<MapMemberListResponse> getMembers(
        @PathVariable Long mapId,
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long userId
    ) {
        return ApiResponse.success(mapService.getMembers(mapId, userId));
    }

    @Operation(summary = "멤버 역할 조회", description = "지도 유형과 해당 사용자의 역할을 반환한다. (place-service 승인 판단용)")
    @GetMapping("/{mapId}/members/{userId}")
    public ApiResponse<MapMemberRoleResponse> getMemberRole(
        @PathVariable Long mapId,
        @PathVariable Long userId
    ) {
        return ApiResponse.success(mapService.getMemberRole(mapId, userId));
    }

    @Operation(summary = "멤버 역할 변경", description = "지도 소유자(OWNER)가 멤버의 역할을 ADMIN 또는 MEMBER로 변경한다.")
    @PutMapping("/{mapId}/members/{userId}/role")
    public ApiResponse<MapMemberRoleUpdateResponse> changeMemberRole(
        @PathVariable Long mapId,
        @PathVariable Long userId,
        @Parameter(hidden = true) @RequestHeader(USER_ID_HEADER) Long requesterId,
        @Valid @RequestBody MapMemberRoleUpdateRequest request
    ) {
        return ApiResponse.success(mapService.changeMemberRole(mapId, requesterId, userId, request));
    }
}
