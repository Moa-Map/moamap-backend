package com.moamap.user.notice.dto;

import com.moamap.user.notice.entity.Notice;
import java.time.Instant;

/**
 * 공지 한 건. 목록 화면이 본문 미리보기를 보여주므로 목록에도 본문을 담는다(자르기는 앱이 한다).
 */
public record NoticeResponse(
        Long noticeId,
        String title,
        String content,
        Instant createdAt,
        Instant updatedAt
) {

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(),
                notice.getCreatedAt(), notice.getUpdatedAt());
    }
}
