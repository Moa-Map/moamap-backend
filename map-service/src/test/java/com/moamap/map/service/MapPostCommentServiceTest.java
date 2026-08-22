package com.moamap.map.service;

import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.dto.MapPostCommentCreateRequest;
import com.moamap.map.dto.MapPostCommentResponse;
import com.moamap.map.dto.PageResponse;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MapPostCommentServiceTest {

    @Mock
    private MapPostCommentRepository commentRepository;

    @Mock
    private MapPostRepository mapPostRepository;

    @Mock
    private MapEntityRepository mapRepository;

    @Mock
    private MapMemberRepository mapMemberRepository;

    private MapPostCommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new MapPostCommentService(commentRepository, mapPostRepository,
            new MapPostAccessPolicy(mapRepository, mapMemberRepository));
    }

    private MapEntity map(MapType type) {
        return MapEntity.create("성수 카페", "설명", null, type, 1L, null, null);
    }

    private MapPost post() {
        return MapPost.create(10L, 2L, "본문", List.of(), List.of());
    }

    @Test
    void 커뮤니티_지도에서도_댓글_작성은_멤버만_가능하다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(10L, 7L,
            new MapPostCommentCreateRequest("저도 가봤어요"), 99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NOT_MAP_MEMBER);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void 멤버는_댓글을_작성할_수_있다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 3L))
            .willReturn(Optional.of(MapMember.of(10L, 3L, MapRole.MEMBER)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post()));
        willAnswer(i -> i.getArgument(0)).given(commentRepository).save(any(MapPostComment.class));

        MapPostCommentResponse response = commentService.create(10L, 7L,
            new MapPostCommentCreateRequest("저도 가봤어요"), 3L);

        assertThat(response.content()).isEqualTo("저도 가봤어요");
        assertThat(response.userId()).isEqualTo(3L);
    }

    @Test
    void 삭제된_게시물의_댓글은_조회되지_않는다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.findAll(10L, 7L, PageRequest.of(0, 20), 3L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.MAP_POST_NOT_FOUND);
    }

    @Test
    void 커뮤니티_지도의_댓글_목록은_비로그인도_조회할_수_있다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post()));
        given(commentRepository.findByMapPostIdAndDeletedAtIsNull(eq(7L), any()))
            .willReturn(new PageImpl<>(List.of(MapPostComment.create(7L, 2L, "댓글")), PageRequest.of(0, 20), 1));

        PageResponse<MapPostCommentResponse> result =
            commentService.findAll(10L, 7L, PageRequest.of(0, 20), null);

        assertThat(result.content()).hasSize(1);
        verify(mapMemberRepository, never()).findByMapIdAndUserId(any(), any());
    }

    @Test
    void 댓글_삭제는_작성자가_아니어도_ADMIN이면_가능하다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 3L))
            .willReturn(Optional.of(MapMember.of(10L, 3L, MapRole.ADMIN)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post()));
        MapPostComment target = MapPostComment.create(7L, 2L, "댓글");
        given(commentRepository.findByIdAndDeletedAtIsNull(4L)).willReturn(Optional.of(target));

        commentService.delete(10L, 7L, 4L, 3L);

        assertThat(target.getDeletedAt()).isNotNull();
    }

    @Test
    void 댓글_삭제는_작성자_본인이면_관리_역할이_아니어도_가능하다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 2L))
            .willReturn(Optional.of(MapMember.of(10L, 2L, MapRole.MEMBER)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post()));
        MapPostComment target = MapPostComment.create(7L, 2L, "댓글");
        given(commentRepository.findByIdAndDeletedAtIsNull(4L)).willReturn(Optional.of(target));

        commentService.delete(10L, 7L, 4L, 2L);

        assertThat(target.getDeletedAt()).isNotNull();
    }

    @Test
    void 댓글_삭제는_작성자도_아니고_관리_역할도_아니면_거부된다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 3L))
            .willReturn(Optional.of(MapMember.of(10L, 3L, MapRole.MEMBER)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post()));
        MapPostComment target = MapPostComment.create(7L, 2L, "댓글");
        given(commentRepository.findByIdAndDeletedAtIsNull(4L)).willReturn(Optional.of(target));

        assertThatThrownBy(() -> commentService.delete(10L, 7L, 4L, 3L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NO_MANAGE_PERMISSION);
        assertThat(target.getDeletedAt()).isNull();
    }

    @Test
    void 다른_게시물의_댓글_id로_삭제하면_404다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 3L))
            .willReturn(Optional.of(MapMember.of(10L, 3L, MapRole.ADMIN)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post()));
        given(commentRepository.findByIdAndDeletedAtIsNull(4L))
            .willReturn(Optional.of(MapPostComment.create(99L, 2L, "다른 게시물 댓글")));

        assertThatThrownBy(() -> commentService.delete(10L, 7L, 4L, 3L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.MAP_POST_COMMENT_NOT_FOUND);
    }
}
