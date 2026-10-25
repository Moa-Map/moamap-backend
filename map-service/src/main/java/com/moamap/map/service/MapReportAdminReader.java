package com.moamap.map.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.moamap.map.dto.MapReportAdminResponse;
import com.moamap.map.dto.MapReportAdminResponse.Content;
import com.moamap.map.entity.MapContentReport;
import com.moamap.map.entity.MapEntity;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import com.moamap.map.entity.ReportTargetType;
import com.moamap.map.repository.MapContentReportRepository;
import com.moamap.map.repository.MapContentReportRepository.TargetReportCount;
import com.moamap.map.repository.MapEntityRepository;
import com.moamap.map.repository.MapPostCommentRepository;
import com.moamap.map.repository.MapPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 신고 목록의 DB 조회 부분. 신고 대상을 종류별로 묶어 한 번씩만 조회한다(신고 수만큼 조회하지 않는다).
 */
@Component
@RequiredArgsConstructor
class MapReportAdminReader {

    private final MapContentReportRepository reportRepository;
    private final MapEntityRepository mapRepository;
    private final MapPostRepository mapPostRepository;
    private final MapPostCommentRepository mapPostCommentRepository;

    @Transactional(readOnly = true)
    public Page<MapReportAdminResponse> read(ReportTargetType targetType, Pageable pageable) {
        Page<MapContentReport> reports = targetType == null
            ? reportRepository.findAllBy(pageable)
            : reportRepository.findByTargetType(targetType, pageable);

        Map<ReportTargetType, List<Long>> idsByType = reports.stream().collect(Collectors.groupingBy(
            MapContentReport::getTargetType,
            Collectors.mapping(MapContentReport::getTargetId, Collectors.collectingAndThen(Collectors.toSet(), List::copyOf))));

        Map<Long, MapEntity> maps = byId(mapRepository.findAllById(idsByType.getOrDefault(ReportTargetType.MAP, List.of())),
            MapEntity::getId);
        Map<Long, MapPost> posts = byId(mapPostRepository.findAllById(idsByType.getOrDefault(ReportTargetType.MAP_POST, List.of())),
            MapPost::getId);
        Map<Long, MapPostComment> comments = byId(mapPostCommentRepository.findAllById(
            idsByType.getOrDefault(ReportTargetType.MAP_POST_COMMENT, List.of())), MapPostComment::getId);
        Map<String, Long> counts = idsByType.entrySet().stream()
            .flatMap(entry -> reportRepository.countByTargets(entry.getKey(), entry.getValue()).stream())
            .collect(Collectors.toMap(row -> key(row.getTargetType(), row.getTargetId()), TargetReportCount::getReportCount));

        return reports.map(report -> MapReportAdminResponse.of(report,
            counts.getOrDefault(key(report.getTargetType(), report.getTargetId()), 0L),
            contentOf(report, maps, posts, comments)));
    }

    private static Content contentOf(MapContentReport report, Map<Long, MapEntity> maps,
                                     Map<Long, MapPost> posts, Map<Long, MapPostComment> comments) {
        return switch (report.getTargetType()) {
            case MAP -> {
                MapEntity map = maps.get(report.getTargetId());
                yield map == null ? Content.missing()
                    : new Content(false, map.getName(), map.getDescription(),
                        map.getImageUrl() == null ? List.of() : List.of(map.getImageUrl()), map.getOwnerId());
            }
            case MAP_POST -> {
                MapPost post = posts.get(report.getTargetId());
                yield post == null ? Content.missing()
                    : new Content(post.getDeletedAt() != null, null, post.getContent(),
                        List.copyOf(post.getImageUrls()), post.getUserId());
            }
            case MAP_POST_COMMENT -> {
                MapPostComment comment = comments.get(report.getTargetId());
                yield comment == null ? Content.missing()
                    : new Content(comment.getDeletedAt() != null, null, comment.getContent(), List.of(),
                        comment.getUserId());
            }
        };
    }

    private static <T> Map<Long, T> byId(List<T> entities, Function<T, Long> idOf) {
        return entities.stream().collect(Collectors.toMap(idOf, Function.identity()));
    }

    private static String key(ReportTargetType type, Long id) {
        return type + ":" + id;
    }
}
