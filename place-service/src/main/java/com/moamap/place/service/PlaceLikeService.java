package com.moamap.place.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.common.exception.CommonErrorCode;
import com.moamap.place.dto.PlaceLikeResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 하트.
 *
 * 누르기(POST)와 취소(DELETE)를 나눈다. 토글 하나로 두면 더블탭이나 재시도가 상태를 뒤집어
 * "눌렀는데 취소됨"이 된다. 나눠 두면 같은 요청을 몇 번 보내도 결과가 같다.
 *
 * 중복 요청에 409를 주지 않는다 — 이미 눌러 둔 상태에서 또 누르면 사용자가 원한 상태는 이미
 * 달성돼 있다. 에러로 답하면 프론트가 "실패했지만 사실 성공"을 따로 처리해야 한다.
 * (MapService.createPersonalMapIfAbsent가 중복 이벤트를 정상 종료로 다루는 것과 같은 판단)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceLikeService {

    private static final String DUPLICATE_LIKE_CONSTRAINT = "uk_place_likes_place_user";

    private final PlaceLikeWriter placeLikeWriter;
    private final PlaceRepository placeRepository;
    private final MapClient mapClient;

    /**
     * 클래스에 @Transactional(readOnly = true)가 걸려 있으므로, 쓰기 단위가 바깥 트랜잭션에 묶이지 않도록
     * NOT_SUPPORTED로 명시한다. 실제 쓰기는 PlaceLikeWriter가 REQUIRES_NEW 트랜잭션에서 수행한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PlaceLikeResponse like(Long placeId, Long userId) {
        requireMapMemberOf(placeId, userId);
        boolean changed;
        try {
            changed = placeLikeWriter.likeIfAbsent(placeId, userId);
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateLikeConstraintViolation(e)) {
                throw e;
            }
            // 같은 사용자의 요청이 동시에 먼저 들어왔다. 목표 상태(하트 눌림)는 이미 달성됐으므로 계속 진행한다.
            changed = false;
        }
        return PlaceLikeResponse.of(placeId, likeCountAfter(placeId, changed), true);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PlaceLikeResponse unlike(Long placeId, Long userId) {
        requireMapMemberOf(placeId, userId);
        boolean changed = placeLikeWriter.unlikeIfPresent(placeId, userId);
        return PlaceLikeResponse.of(placeId, likeCountAfter(placeId, changed), false);
    }

    /**
     * 바뀐 게 없으면 Place를 갱신하지 않고 현재 수만 읽는다.
     *
     * 더블탭·재시도로 들어오는 요청은 실제로 아무것도 바꾸지 않는데, 그때마다 places를 UPDATE하면
     * 인기 있는 장소일수록 같은 행에 쓰기가 몰려 잠금 경합만 커진다. 값이 그대로인데 쓸 이유가 없다.
     */
    private long likeCountAfter(Long placeId, boolean changed) {
        return changed ? placeLikeWriter.refreshLikeCount(placeId) : placeLikeWriter.currentLikeCount(placeId);
    }

    // 길이 초과 등 다른 무결성 위반까지 "이미 눌림"으로 뭉개지 않도록, 해당 제약 위반일 때만 통과시킨다.
    private boolean isDuplicateLikeConstraintViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null && constraintName.toLowerCase().contains(DUPLICATE_LIKE_CONSTRAINT);
        }
        return false;
    }

    /** 하트는 쓰기다. 댓글 작성·로그 탭 작성과 같이 해당 지도의 멤버만 할 수 있다. */
    private void requireMapMemberOf(Long placeId, Long userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Place place = placeRepository.findByIdAndDeletedAtIsNull(placeId)
            .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
        if (mapClient.getMemberInfo(place.getMapId(), userId).role() == MapMemberRole.NONE) {
            throw new BusinessException(PlaceErrorCode.NOT_MAP_MEMBER);
        }
    }
}
