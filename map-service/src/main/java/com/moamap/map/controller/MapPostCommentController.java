package com.moamap.map.controller;

import com.moamap.common.response.ApiResponse;
import com.moamap.map.dto.MapPostCommentCreateRequest;
import com.moamap.map.dto.MapPostCommentResponse;
import com.moamap.map.dto.PageResponse;
import com.moamap.map.service.MapPostCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MapPostComment", description = "지도 로그 탭 게시물 댓글 API")
@RestController
@RequestMapping("/api/v1/maps/{mapId}/posts/{postId}/comments")
@RequiredArgsConstructor
public class MapPostCommentController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final MapPostCommentService mapPostCommentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "댓글 작성",
        description = "지도 멤버만 작성할 수 있다. 읽기가 열려 있는 커뮤니티 지도에서도 작성은 멤버여야 한다."
    )
    public ApiResponse<MapPostCommentResponse> create(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @Parameter(description = "게시물 ID", example = "7") @PathVariable Long postId,
        @Valid @RequestBody MapPostCommentCreateRequest request,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(mapPostCommentService.create(mapId, postId, request, userId));
    }

    @GetMapping
    @Operation(
        summary = "댓글 목록 조회",
        description = "작성순으로 반환한다. 조회 권한은 게시물 조회와 같다. 삭제된 게시물의 댓글은 조회되지 않는다."
    )
    public ApiResponse<PageResponse<MapPostCommentResponse>> getAll(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @Parameter(description = "게시물 ID", example = "7") @PathVariable Long postId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        return ApiResponse.success(mapPostCommentService.findAll(mapId, postId, pageable, userId));
    }

    @DeleteMapping("/{commentId}")
    @Operation(
        summary = "댓글 삭제",
        description = "작성자 본인 또는 지도의 OWNER/ADMIN이 삭제할 수 있다(소프트 삭제)."
    )
    public ApiResponse<Void> delete(
        @Parameter(description = "지도 ID", example = "10") @PathVariable Long mapId,
        @Parameter(description = "게시물 ID", example = "7") @PathVariable Long postId,
        @Parameter(description = "댓글 ID", example = "4") @PathVariable Long commentId,
        @Parameter(hidden = true) @RequestHeader(value = USER_ID_HEADER, required = false) Long userId
    ) {
        mapPostCommentService.delete(mapId, postId, commentId, userId);
        return ApiResponse.success(null);
    }
}
