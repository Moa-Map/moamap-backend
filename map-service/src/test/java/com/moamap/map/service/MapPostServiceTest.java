package com.moamap.map.service;

import java.util.List;
import java.util.Optional;
import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.map.dto.MapPostCreateRequest;
import com.moamap.map.dto.MapPostPlaceTagRequest;
import com.moamap.map.dto.MapPostResponse;
import com.moamap.map.dto.MapPostUpdateRequest;
import com.moamap.map.dto.PageResponse;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.MapType;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapMemberRepository;
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
class MapPostServiceTest {

    @Mock
    private MapPostRepository mapPostRepository;

    @Mock
    private MapEntityRepository mapRepository;

    @Mock
    private MapMemberRepository mapMemberRepository;

    private MapPostService mapPostService;

    @BeforeEach
    void setUp() {
        // 권한 규칙을 목으로 대체하면 검증 대상이 사라지므로 실제 정책을 끼운다.
        mapPostService = new MapPostService(mapPostRepository,
            new MapPostAccessPolicy(mapRepository, mapMemberRepository));
    }

    private MapEntity map(MapType type) {
        return MapEntity.create("성수 카페", "설명", null, type, 1L, null, null);
    }

    private MapPostCreateRequest request() {
        return new MapPostCreateRequest(
            "성수 카페 다녀왔어요",
            List.of("https://cdn.moamap.com/map-posts/1/a.jpg"),
            List.of(new MapPostPlaceTagRequest(5L, "블루보틀 성수점")));
    }

    @Test
    void 멤버는_게시물을_작성할_수_있고_태그_이름은_받은_값이_그대로_저장된다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 2L))
            .willReturn(Optional.of(MapMember.of(10L, 2L, MapRole.MEMBER)));
        willAnswer(invocation -> invocation.getArgument(0)).given(mapPostRepository).saveAndFlush(any(MapPost.class));

        MapPostResponse response = mapPostService.create(10L, request(), 2L);

        assertThat(response.content()).isEqualTo("성수 카페 다녀왔어요");
        assertThat(response.placeTags()).extracting("placeId", "name")
            .containsExactly(org.assertj.core.api.Assertions.tuple(5L, "블루보틀 성수점"));
        assertThat(response.imageUrls()).hasSize(1);
    }

    @Test
    void 비멤버는_커뮤니티_지도에도_게시물을_작성할_수_없다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapPostService.create(10L, request(), 99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NOT_MAP_MEMBER);
        verify(mapPostRepository, never()).saveAndFlush(any());
    }

    @Test
    void 공식_지도에는_로그탭이_없어서_작성_요청이_404다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.OFFICIAL)));

        assertThatThrownBy(() -> mapPostService.create(10L, request(), 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.MAP_POST_NOT_SUPPORTED);
        verify(mapPostRepository, never()).saveAndFlush(any());
    }

    @Test
    void 비로그인은_게시물을_작성할_수_없다() {
        assertThatThrownBy(() -> mapPostService.create(10L, request(), null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.UNAUTHORIZED);
        verify(mapPostRepository, never()).saveAndFlush(any());
    }

    @Test
    void 프라이빗_지도의_멤버는_게시물을_작성할_수_있다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.PRIVATE)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 2L))
            .willReturn(Optional.of(MapMember.of(10L, 2L, MapRole.MEMBER)));
        willAnswer(invocation -> invocation.getArgument(0)).given(mapPostRepository).saveAndFlush(any(MapPost.class));

        MapPostResponse response = mapPostService.create(10L, request(), 2L);

        assertThat(response.content()).isEqualTo("성수 카페 다녀왔어요");
    }

    @Test
    void 같은_장소를_두_번_태그해서_작성하면_거부된다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 2L))
            .willReturn(Optional.of(MapMember.of(10L, 2L, MapRole.MEMBER)));
        MapPostCreateRequest duplicated = new MapPostCreateRequest(
            "성수 카페 다녀왔어요",
            List.of(),
            List.of(new MapPostPlaceTagRequest(5L, "블루보틀 성수점"),
                new MapPostPlaceTagRequest(5L, "블루보틀 성수점")));

        assertThatThrownBy(() -> mapPostService.create(10L, duplicated, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.DUPLICATE_PLACE_TAG);
        verify(mapPostRepository, never()).saveAndFlush(any());
    }

    @Test
    void 같은_장소를_두_번_태그해서_수정하면_거부된다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 2L))
            .willReturn(Optional.of(MapMember.of(10L, 2L, MapRole.MEMBER)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post(10L, 2L)));
        MapPostUpdateRequest duplicated = new MapPostUpdateRequest(null, null,
            List.of(new MapPostPlaceTagRequest(5L, "블루보틀 성수점"),
                new MapPostPlaceTagRequest(5L, "블루보틀 성수점")));

        assertThatThrownBy(() -> mapPostService.update(10L, 7L, duplicated, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.DUPLICATE_PLACE_TAG);
    }

    @Test
    void 프라이빗_지도의_비멤버는_게시물을_작성할_수_없다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.PRIVATE)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapPostService.create(10L, request(), 99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NOT_MAP_MEMBER);
        verify(mapPostRepository, never()).saveAndFlush(any());
    }

    private MapPost post(Long mapId, Long writerId) {
        return MapPost.create(mapId, writerId, "본문", List.of(), List.of());
    }

    @Test
    void 커뮤니티_지도의_게시물은_비로그인도_조회할_수_있다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapPostRepository.findByMapIdAndDeletedAtIsNull(eq(10L), any()))
            .willReturn(new PageImpl<>(List.of(post(10L, 2L)), PageRequest.of(0, 20), 1));

        PageResponse<MapPostResponse> result = mapPostService.findAll(10L, PageRequest.of(0, 20), null);

        assertThat(result.content()).hasSize(1);
        verify(mapMemberRepository, never()).findByMapIdAndUserId(any(), any());
    }

    @Test
    void 프라이빗_지도의_게시물은_비멤버가_조회할_수_없다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.PRIVATE)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mapPostService.findAll(10L, PageRequest.of(0, 20), 99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NOT_MAP_MEMBER);
    }

    @Test
    void 프라이빗_지도의_게시물은_비로그인이_조회할_수_없다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.PRIVATE)));

        assertThatThrownBy(() -> mapPostService.findAll(10L, PageRequest.of(0, 20), null))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void 프라이빗_지도의_게시물은_멤버가_조회할_수_있다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.PRIVATE)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 2L))
            .willReturn(Optional.of(MapMember.of(10L, 2L, MapRole.MEMBER)));
        given(mapPostRepository.findByMapIdAndDeletedAtIsNull(eq(10L), any()))
            .willReturn(new PageImpl<>(List.of(post(10L, 2L)), PageRequest.of(0, 20), 1));

        PageResponse<MapPostResponse> result = mapPostService.findAll(10L, PageRequest.of(0, 20), 2L);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void 공식_지도의_게시물_조회는_404다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.OFFICIAL)));

        assertThatThrownBy(() -> mapPostService.findAll(10L, PageRequest.of(0, 20), 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.MAP_POST_NOT_SUPPORTED);
    }

    @Test
    void 다른_지도의_게시물_id로_조회하면_404다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post(99L, 2L)));

        assertThatThrownBy(() -> mapPostService.findById(10L, 7L, 2L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.MAP_POST_NOT_FOUND);
    }

    @Test
    void 게시물_수정은_작성자만_할_수_있다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 3L))
            .willReturn(Optional.of(MapMember.of(10L, 3L, MapRole.ADMIN)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post(10L, 2L)));

        assertThatThrownBy(() -> mapPostService.update(10L, 7L,
            new MapPostUpdateRequest("수정", null, null), 3L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NO_MANAGE_PERMISSION);
    }

    @Test
    void 게시물_수정은_작성자_본인이면_성공한다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 2L))
            .willReturn(Optional.of(MapMember.of(10L, 2L, MapRole.MEMBER)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post(10L, 2L)));

        MapPostResponse response = mapPostService.update(10L, 7L,
            new MapPostUpdateRequest("수정된 내용", null, null), 2L);

        assertThat(response.content()).isEqualTo("수정된 내용");
    }

    @Test
    void 게시물_수정은_OWNER여도_작성자가_아니면_거부된다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 1L))
            .willReturn(Optional.of(MapMember.of(10L, 1L, MapRole.OWNER)));
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(post(10L, 2L)));

        assertThatThrownBy(() -> mapPostService.update(10L, 7L,
            new MapPostUpdateRequest("수정", null, null), 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NO_MANAGE_PERMISSION);
    }

    @Test
    void 게시물_삭제는_작성자가_아니어도_ADMIN이면_가능하다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 3L))
            .willReturn(Optional.of(MapMember.of(10L, 3L, MapRole.ADMIN)));
        MapPost target = post(10L, 2L);
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(target));

        mapPostService.delete(10L, 7L, 3L);

        assertThat(target.getDeletedAt()).isNotNull();
    }

    @Test
    void 게시물_삭제는_작성자가_아니어도_OWNER면_가능하다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 1L))
            .willReturn(Optional.of(MapMember.of(10L, 1L, MapRole.OWNER)));
        MapPost target = post(10L, 2L);
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(target));

        mapPostService.delete(10L, 7L, 1L);

        assertThat(target.getDeletedAt()).isNotNull();
    }

    @Test
    void 게시물_삭제는_작성자_본인이면_관리_역할이_아니어도_가능하다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 2L))
            .willReturn(Optional.of(MapMember.of(10L, 2L, MapRole.MEMBER)));
        MapPost target = post(10L, 2L);
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(target));

        mapPostService.delete(10L, 7L, 2L);

        assertThat(target.getDeletedAt()).isNotNull();
    }

    @Test
    void 게시물_삭제는_작성자도_아니고_관리_역할도_아니면_거부된다() {
        given(mapRepository.findById(10L)).willReturn(Optional.of(map(MapType.COMMUNITY)));
        given(mapMemberRepository.findByMapIdAndUserId(10L, 3L))
            .willReturn(Optional.of(MapMember.of(10L, 3L, MapRole.MEMBER)));
        MapPost target = post(10L, 2L);
        given(mapPostRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(target));

        assertThatThrownBy(() -> mapPostService.delete(10L, 7L, 3L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", MapErrorCode.NO_MANAGE_PERMISSION);
        assertThat(target.getDeletedAt()).isNull();
    }
}
