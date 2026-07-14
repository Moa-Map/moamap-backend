package com.moamap.place.repository;

import java.util.List;
import java.util.Optional;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByIdAndDeletedAtIsNull(Long id);

    List<Place> findByMapIdAndStatusAndDeletedAtIsNull(Long mapId, PlaceStatus status);
}
