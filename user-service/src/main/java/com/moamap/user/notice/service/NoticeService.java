package com.moamap.user.notice.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.notice.dto.NoticeResponse;
import com.moamap.user.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱에서 보는 공지 조회. 로그인 없이 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    /** 한 번에 내려줄 수 있는 최대 개수. 큰 size로 전체를 한 번에 긁어가는 요청을 막는다. */
    static final int MAX_PAGE_SIZE = 50;

    /** 최신 등록순. 같은 시각에 등록된 공지는 나중에 만든 것이 앞에 온다. */
    private static final Sort LATEST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final NoticeRepository noticeRepository;

    /** 정렬은 서버가 고정한다. 클라이언트가 보낸 sort는 무시한다. */
    public Page<NoticeResponse> getNotices(Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), LATEST);
        return noticeRepository.findByDeletedAtIsNull(latestFirst).map(NoticeResponse::from);
    }

    public NoticeResponse getNotice(Long noticeId) {
        return noticeRepository.findByIdAndDeletedAtIsNull(noticeId)
                .map(NoticeResponse::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.NOTICE_NOT_FOUND));
    }
}
