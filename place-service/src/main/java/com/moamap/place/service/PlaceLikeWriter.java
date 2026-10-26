package com.moamap.place.service;

import com.moamap.place.entity.PlaceLike;
import com.moamap.place.repository.PlaceLikeRepository;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하트의 쓰기 단위. 반드시 PlaceLikeService와 별도 빈이어야 한다(PlaceBulkRegistrar와 같은 이유).
 *
 * JPA는 유니크 제약 위반이 한 번 나면 그 트랜잭션을 rollback-only로 표시한다. 하트는 중복 요청에
 * 에러가 아니라 현재 상태로 답해야 하므로, 위반 후에도 카운트를 다시 세어 응답을 만들어야 한다.
 * 삽입만 REQUIRES_NEW로 격리하면 실패가 그 트랜잭션 안에서 끝나고 바깥 흐름은 계속 진행할 수 있다.
 * 같은 클래스 안에서 자기 메서드를 부르면 프록시를 타지 않아 REQUIRES_NEW가 조용히 무시되므로 클래스를 분리했다.
 *
 * 예외를 이 안에서 잡아 정상 반환하면 안 된다. rollback-only인 채로 커밋을 시도해
 * UnexpectedRollbackException이 난다. 호출자가 잡도록 밖으로 던진다.
 */
@Component
@RequiredArgsConstructor
public class PlaceLikeWriter {

    private final PlaceLikeRepository placeLikeRepository;
    private final PlaceRepository placeRepository;

    /**
     * 아직 없으면 하트를 남긴다. 이미 있으면 아무것도 하지 않고 false를 준다.
     *
     * 사전 조회는 흔한 중복(더블탭·재시도)을 예외 없이 싸게 걸러내기 위한 것이고,
     * 같은 순간에 겹친 요청은 uk_place_likes_place_user가 최종 방어선이다.
     *
     * 실제로 바뀌었는지를 돌려주는 이유는 호출부가 카운트 갱신을 건너뛸 수 있게 하기 위함이다
     * (PlaceLikeService 참고).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean likeIfAbsent(Long placeId, Long userId) {
        if (placeLikeRepository.existsByPlaceIdAndUserId(placeId, userId)) {
            return false;
        }
        placeLikeRepository.saveAndFlush(PlaceLike.of(placeId, userId));
        return true;
    }

    /** 눌러 둔 하트가 없으면 0건 삭제로 끝난다 — 취소는 몇 번을 해도 같은 결과다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean unlikeIfPresent(Long placeId, Long userId) {
        return placeLikeRepository.deleteLike(placeId, userId) > 0;
    }

    /** 지금 저장된 하트 수. 아무것도 바뀌지 않았을 때 Place를 건드리지 않고 값만 읽는 용도다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long currentLikeCount(Long placeId) {
        return placeLikeRepository.countByPlaceId(placeId);
    }

    /**
     * place_likes를 다시 세어 Place.likeCount에 반영하고 그 값을 돌려준다.
     *
     * 증감이 아니라 재계산이다. 하트는 취소로 행이 지워지므로 한 번 어긋나면 되돌릴 방법이 없는데,
     * 재계산은 매번 사실과 맞춰진다. place_likes(place_id) 인덱스가 있어 현재 규모에서 COUNT는 싸다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long refreshLikeCount(Long placeId) {
        long likeCount = placeLikeRepository.countByPlaceId(placeId);
        placeRepository.updateLikeCount(placeId, (int) likeCount);
        return likeCount;
    }
}
