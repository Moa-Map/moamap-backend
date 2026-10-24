package com.moamap.map.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import com.moamap.map.config.JpaAuditingConfig;
import com.moamap.map.entity.MapPost;
import com.moamap.map.entity.MapPostComment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * 차단한 사용자의 로그·로그 댓글을 빼는 조회가 실제 DB에서 맞게 동작하는지 확인한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class BlockedUserFilterQueryTest {

    private static final Long MAP_ID = 10L;
    private static final Long BLOCKED = 5L;
    private static final Long OTHER = 6L;

    @Autowired private MapPostRepository mapPostRepository;
    @Autowired private MapPostCommentRepository mapPostCommentRepository;

    @Test
    void 차단한_사람의_로그를_빼고_페이지_수도_맞게_센다() {
        post(BLOCKED);
        MapPost visible = post(OTHER);

        var page = mapPostRepository.findByMapIdAndDeletedAtIsNullAndUserIdNotIn(MAP_ID, List.of(BLOCKED),
            PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(MapPost::getId).containsExactly(visible.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 차단한_사람의_로그_댓글을_뺀다() {
        MapPost post = post(OTHER);
        mapPostCommentRepository.saveAndFlush(MapPostComment.create(post.getId(), BLOCKED, "차단한 사람 댓글"));
        MapPostComment visible = mapPostCommentRepository.saveAndFlush(MapPostComment.create(post.getId(), OTHER, "댓글"));

        var page = mapPostCommentRepository.findByMapPostIdAndDeletedAtIsNullAndUserIdNotIn(post.getId(),
            List.of(BLOCKED), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(MapPostComment::getId).containsExactly(visible.getId());
    }

    private MapPost post(Long userId) {
        return mapPostRepository.saveAndFlush(MapPost.create(MAP_ID, userId, "로그", List.of(), List.of()));
    }
}
