package com.moamap.user.notice.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.notice.dto.NoticeCreateRequest;
import com.moamap.user.notice.dto.NoticeResponse;
import com.moamap.user.notice.dto.NoticeUpdateRequest;
import com.moamap.user.notice.entity.Notice;
import com.moamap.user.notice.repository.NoticeRepository;
import com.moamap.user.user.service.AdminGuard;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 공지 등록·수정·삭제.
 *
 * 권한 확인을 공지 조회보다 먼저 한다. 관리자가 아닌 사람이 없는 공지 ID로 요청해도 404가 아니라 403을 받아,
 * 응답으로 공지 존재 여부를 알아낼 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NoticeAdminService {

    private final NoticeRepository noticeRepository;
    private final AdminGuard adminGuard;

    public NoticeResponse create(Long adminId, NoticeCreateRequest request) {
        adminGuard.requireAdmin(adminId);
        Notice notice = noticeRepository.save(Notice.create(request.title(), request.content(), adminId));
        return NoticeResponse.from(notice);
    }

    public NoticeResponse update(Long adminId, Long noticeId, NoticeUpdateRequest request) {
        adminGuard.requireAdmin(adminId);
        Notice notice = getActiveNotice(noticeId);
        notice.update(request.title(), request.content());
        // 응답에 바뀐 수정 시각을 담으려면 지금 반영해야 한다(@PreUpdate는 flush 때 실행된다).
        noticeRepository.flush();
        return NoticeResponse.from(notice);
    }

    public void delete(Long adminId, Long noticeId) {
        adminGuard.requireAdmin(adminId);
        getActiveNotice(noticeId).delete(Instant.now());
    }

    private Notice getActiveNotice(Long noticeId) {
        return noticeRepository.findByIdAndDeletedAtIsNull(noticeId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.NOTICE_NOT_FOUND));
    }
}
