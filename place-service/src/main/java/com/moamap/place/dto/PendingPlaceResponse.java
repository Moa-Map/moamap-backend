package com.moamap.place.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.moamap.place.entity.Place;

public record PendingPlaceResponse(
    Long id,
    String name,
    String address,
    String description,
    List<String> photoUrls,
    String createdByNickname,
    String createdByProfileImageUrl,
    LocalDateTime createdAt
) {

    public static PendingPlaceResponse of(Place place, String createdByNickname, String createdByProfileImageUrl) {
        return new PendingPlaceResponse(
            place.getId(),
            place.getName(),
            place.getAddress(),
            place.getDescription(),
            new ArrayList<>(place.getPhotoUrls()),
            createdByNickname,
            createdByProfileImageUrl,
            place.getCreatedAt()
        );
    }
}
