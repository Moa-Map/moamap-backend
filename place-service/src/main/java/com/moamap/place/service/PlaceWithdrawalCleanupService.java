package com.moamap.place.service;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.place.repository.PlaceCommentRepository;
import com.moamap.place.repository.PlaceLikeRepository;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 place-service 데이터를 정리한다.
 *
 * - 하트를 지우고 그 장소들의 하트 수를 다시 센다.
 * - 장소 댓글을 지우고(소프트 삭제) 그 장소들의 댓글 수·평점을 다시 계산한다.
 * - 등록한 장소와 신고 기록은 남긴다. 장소는 지도에 속한 데이터이고, 신고는 운영 기록이다.
 *
 * 같은 이벤트가 다시 와도 결과가 같다. 지울 하트·댓글이 없으면 다시 셀 장소도 없다.
 */
@Service
@RequiredArgsConstructor
public class PlaceWithdrawalCleanupService {

    private final PlaceLikeRepository placeLikeRepository;
    private final PlaceCommentRepository placeCommentRepository;
    private final PlaceRepository placeRepository;
    private final PlaceCommentService placeCommentService;

    @Transactional
    public void cleanUp(Long userId) {
        removeLikes(userId);
        removeComments(userId);
    }

    /**
     * 하트 수는 같은 트랜잭션 안에서 다시 센다. PlaceLikeWriter.refreshLikeCount는 별도 트랜잭션(REQUIRES_NEW)이라
     * 여기서 부르면 아직 커밋되지 않은 삭제를 보지 못해, 지운 하트까지 센 값이 들어간다.
     */
    private void removeLikes(Long userId) {
        List<Long> placeIds = placeLikeRepository.findPlaceIdsByUserId(userId);
        if (placeIds.isEmpty()) {
            return;
        }
        placeLikeRepository.deleteAllByUserId(userId);
        for (Long placeId : placeIds) {
            placeRepository.updateLikeCount(placeId, (int) placeLikeRepository.countByPlaceId(placeId));
        }
    }

    private void removeComments(Long userId) {
        List<Long> placeIds = placeCommentRepository.findPlaceIdsOfActiveCommentsByUserId(userId);
        if (placeIds.isEmpty()) {
            return;
        }
        placeCommentRepository.softDeleteAllByUserId(userId, LocalDateTime.now());
        placeIds.forEach(placeCommentService::refreshPlaceCommentSummary);
    }
}
