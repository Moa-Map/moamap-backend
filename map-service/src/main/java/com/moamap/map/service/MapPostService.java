package com.moamap.map.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.moamap.common.exception.BusinessException;
import com.moamap.map.dto.MapPostCreateRequest;
import com.moamap.map.dto.MapPostPlaceTagRequest;
import com.moamap.map.dto.MapPostResponse;
import com.moamap.map.dto.MapPostUpdateRequest;
import com.moamap.map.dto.PageResponse;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapRole;
import com.moamap.map.entity.PlaceTag;
import com.moamap.map.exception.MapErrorCode;
import com.moamap.map.repository.MapPostRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그 탭 게시물. 권한 규칙은 이슈 #95의 권한·상태 분기 표를 따른다.
 *
 * 장소 태그는 검증하지 않는다. 이름 스냅샷을 서버가 채우려면 place-service를 불러야 하는데,
 * 프론트가 태그 피커에서 이미 아는 값을 함께 보내주면 그 호출이 사라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapPostService {

    private static final String DUPLICATE_PLACE_TAG_CONSTRAINT = "uk_map_post_place_tags_post_place";

    private final MapPostRepository mapPostRepository;
    private final MapPostAccessPolicy accessPolicy;

    @Transactional
    public MapPostResponse create(Long mapId, MapPostCreateRequest request, Long userId) {
        accessPolicy.requireWritable(mapId, userId);

        MapPost post = MapPost.create(mapId, userId, request.content(),
            request.imageUrls(), toPlaceTags(request.placeTags()));
        // 요청 안 중복은 toPlaceTags가 미리 막고, 동시 요청 race condition은
        // uk_map_post_place_tags_post_place 유니크 제약으로 막는다.
        try {
            return MapPostResponse.from(mapPostRepository.saveAndFlush(post));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicatePlaceTagConstraintViolation(e)) {
                throw new BusinessException(MapErrorCode.DUPLICATE_PLACE_TAG);
            }
            throw e;
        }
    }

    public PageResponse<MapPostResponse> findAll(Long mapId, Pageable pageable, Long userId) {
        accessPolicy.requireReadable(mapId, userId);
        return PageResponse.from(
            mapPostRepository.findByMapIdAndDeletedAtIsNull(mapId, pageable).map(MapPostResponse::from));
    }

    public MapPostResponse findById(Long mapId, Long postId, Long userId) {
        accessPolicy.requireReadable(mapId, userId);
        return MapPostResponse.from(getPostOrThrow(mapId, postId));
    }

    @Transactional
    public MapPostResponse update(Long mapId, Long postId, MapPostUpdateRequest request, Long userId) {
        accessPolicy.requireWritable(mapId, userId);

        MapPost post = getPostOrThrow(mapId, postId);
        if (!post.isWrittenBy(userId)) {
            throw new BusinessException(MapErrorCode.NO_MANAGE_PERMISSION);
        }
        post.update(request.content(), request.imageUrls(), toPlaceTags(request.placeTags()));
        try {
            mapPostRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (isDuplicatePlaceTagConstraintViolation(e)) {
                throw new BusinessException(MapErrorCode.DUPLICATE_PLACE_TAG);
            }
            throw e;
        }
        return MapPostResponse.from(post);
    }

    @Transactional
    public void delete(Long mapId, Long postId, Long userId) {
        MapRole role = accessPolicy.requireWritable(mapId, userId);

        MapPost post = getPostOrThrow(mapId, postId);
        if (!accessPolicy.canDelete(post.isWrittenBy(userId), role)) {
            throw new BusinessException(MapErrorCode.NO_MANAGE_PERMISSION);
        }
        post.delete();
    }

    private MapPost getPostOrThrow(Long mapId, Long postId) {
        MapPost post = mapPostRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new BusinessException(MapErrorCode.MAP_POST_NOT_FOUND));
        // 다른 지도의 게시물 id로 들어오면 없는 것으로 다룬다.
        if (!post.getMapId().equals(mapId)) {
            throw new BusinessException(MapErrorCode.MAP_POST_NOT_FOUND);
        }
        return post;
    }

    private List<PlaceTag> toPlaceTags(List<MapPostPlaceTagRequest> tags) {
        if (tags == null) {
            return null;
        }
        List<PlaceTag> placeTags = tags.stream().map(MapPostPlaceTagRequest::toEntity).toList();
        Set<Long> placeIds = new HashSet<>();
        for (PlaceTag placeTag : placeTags) {
            if (!placeIds.add(placeTag.placeId())) {
                throw new BusinessException(MapErrorCode.DUPLICATE_PLACE_TAG);
            }
        }
        return placeTags;
    }

    // 길이 초과, NOT NULL 등 다른 무결성 위반까지 DUPLICATE_PLACE_TAG로 뭉개지 않도록,
    // uk_map_post_place_tags_post_place 제약 위반일 때만 변환한다.
    private boolean isDuplicatePlaceTagConstraintViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null && constraintName.toLowerCase().contains(DUPLICATE_PLACE_TAG_CONSTRAINT);
        }
        return false;
    }
}
