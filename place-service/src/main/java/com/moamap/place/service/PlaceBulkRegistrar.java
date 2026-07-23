package com.moamap.place.service;

import java.util.ArrayList;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PlaceBulkCreateRequest;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일괄 등록의 단건 처리. 반드시 PlaceService와 별도 빈이어야 한다.
 *
 * JPA는 유니크 제약 위반이 한 번 나면 그 트랜잭션을 rollback-only로 표시해서,
 * 한 트랜잭션 안에서 "3건 실패, 17건 커밋"이 불가능하다. 그래서 건별로
 * REQUIRES_NEW 트랜잭션을 연다. 같은 클래스 안에서 자기 메서드를 호출하면
 * 프록시를 타지 않아 REQUIRES_NEW가 조용히 무시되므로 클래스를 분리했다.
 *
 * 예외를 이 안에서 잡아 정상 반환하면 안 된다. 트랜잭션이 rollback-only인 채로
 * 커밋을 시도해 UnexpectedRollbackException이 난다. 호출자가 잡도록 밖으로 던진다.
 */
@Component
@RequiredArgsConstructor
public class PlaceBulkRegistrar {

    private final PlaceRepository placeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long register(PlaceBulkCreateRequest.Item item, Long mapId, Long userId, PlaceStatus status) {
        if (placeRepository.existsByMapIdAndKakaoPlaceIdAndDeletedAtIsNull(mapId, item.kakaoPlaceId())) {
            throw new BusinessException(PlaceErrorCode.DUPLICATE_PLACE);
        }
        Place place = Place.builder()
            .name(item.name())
            .address(item.address())
            .roadAddress(item.roadAddress())
            .lat(item.lat())
            .lng(item.lng())
            .category(item.category())
            .kakaoPlaceId(item.kakaoPlaceId())
            .sourceType(item.sourceType())
            .sourceUrl(item.sourceUrl())
            .description(item.description())
            .tags(item.tags() == null ? new ArrayList<>() : new ArrayList<>(item.tags()))
            .mapId(mapId)
            .createdBy(userId)
            .status(status)
            .build();
        return placeRepository.saveAndFlush(place).getId();
    }
}
