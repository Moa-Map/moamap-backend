package com.moamap.place.service;

import com.moamap.place.dto.PlaceReportAdminResponse;
import com.moamap.place.dto.PlaceReportAdminResponse.Content;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceComment;
import com.moamap.place.entity.PlaceCommentReport;
import com.moamap.place.entity.PlaceReport;
import com.moamap.place.entity.PlaceReportTargetType;
import com.moamap.place.repository.PlaceCommentReportRepository;
import com.moamap.place.repository.PlaceCommentReportRepository.CommentCountRow;
import com.moamap.place.repository.PlaceCommentRepository;
import com.moamap.place.repository.PlaceReportRepository;
import com.moamap.place.repository.PlaceReportRepository.PlaceCountRow;
import com.moamap.place.repository.PlaceRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 신고 목록의 DB 조회 부분. 한 페이지의 대상을 한 번에 조회한다(신고 수만큼 조회하지 않는다).
 * 지워진 대상도 신고 기록은 남으므로, 지워졌다고 표시해 함께 보여준다.
 */
@Component
@RequiredArgsConstructor
class PlaceReportAdminReader {

    private final PlaceReportRepository placeReportRepository;
    private final PlaceCommentReportRepository placeCommentReportRepository;
    private final PlaceRepository placeRepository;
    private final PlaceCommentRepository placeCommentRepository;

    @Transactional(readOnly = true)
    public Page<PlaceReportAdminResponse> readPlaceReports(Pageable pageable) {
        Page<PlaceReport> reports = placeReportRepository.findAllBy(pageable);
        List<Long> placeIds = reports.stream().map(PlaceReport::getPlaceId).distinct().toList();
        Map<Long, Place> places = placesById(placeIds);
        Map<Long, Long> counts = placeIds.isEmpty() ? Map.of()
            : placeReportRepository.countByPlaceIds(placeIds).stream()
                .collect(Collectors.toMap(PlaceCountRow::getPlaceId, PlaceCountRow::getReportCount));

        return reports.map(report -> {
            Place place = places.get(report.getPlaceId());
            Content content = place == null ? Content.missing()
                : new Content(place.getDeletedAt() != null, place.getName(), place.getDescription(),
                    List.copyOf(place.getPhotoUrls()), place.getCreatedBy());
            return new PlaceReportAdminResponse(report.getId(), PlaceReportTargetType.PLACE, report.getPlaceId(),
                report.getPlaceId(), report.getMapId(), report.getReporterId(), report.getReason(), report.getDetail(),
                report.getCreatedAt(), counts.getOrDefault(report.getPlaceId(), 0L), content);
        });
    }

    @Transactional(readOnly = true)
    public Page<PlaceReportAdminResponse> readCommentReports(Pageable pageable) {
        Page<PlaceCommentReport> reports = placeCommentReportRepository.findAllBy(pageable);
        List<Long> commentIds = reports.stream().map(PlaceCommentReport::getPlaceCommentId).distinct().toList();
        Map<Long, PlaceComment> comments = placeCommentRepository.findAllById(commentIds).stream()
            .collect(Collectors.toMap(PlaceComment::getId, Function.identity()));
        Map<Long, Place> places = placesById(comments.values().stream().map(PlaceComment::getPlaceId).distinct().toList());
        Map<Long, Long> counts = commentIds.isEmpty() ? Map.of()
            : placeCommentReportRepository.countByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(CommentCountRow::getPlaceCommentId, CommentCountRow::getReportCount));

        return reports.map(report -> {
            PlaceComment comment = comments.get(report.getPlaceCommentId());
            Place place = comment == null ? null : places.get(comment.getPlaceId());
            Content content = comment == null ? Content.missing()
                : new Content(comment.getDeletedAt() != null, place == null ? null : place.getName(),
                    comment.getContent(), List.copyOf(comment.getImageUrls()), comment.getUserId());
            return new PlaceReportAdminResponse(report.getId(), PlaceReportTargetType.PLACE_COMMENT,
                report.getPlaceCommentId(), comment == null ? null : comment.getPlaceId(),
                place == null ? null : place.getMapId(), report.getReporterId(), report.getReason(), report.getDetail(),
                report.getCreatedAt(), counts.getOrDefault(report.getPlaceCommentId(), 0L), content);
        });
    }

    private Map<Long, Place> placesById(Collection<Long> placeIds) {
        return placeRepository.findAllById(placeIds).stream().collect(Collectors.toMap(Place::getId, Function.identity()));
    }
}
