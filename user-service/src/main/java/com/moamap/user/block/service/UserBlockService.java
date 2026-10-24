package com.moamap.user.block.service;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.block.dto.BlockedUserResponse;
import com.moamap.user.block.entity.UserBlock;
import com.moamap.user.block.repository.UserBlockRepository;
import com.moamap.user.exception.UserErrorCode;
import com.moamap.user.report.service.UserReportService;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 차단. 한쪽 방향이며, 차단한 사람의 화면에서 차단된 사람의 콘텐츠가 사라진다(각 서비스가 목록에서 뺀다).
 *
 * 차단하면 운영자에게도 알린다(앱스토어 심사 요건). 사유 BLOCKED인 사용자 신고로 남겨 운영자 신고 목록과 알림을 함께 쓴다.
 * 같은 사람을 다시 차단하거나 차단하지 않은 사람을 해제해도 성공으로 둔다 — 앱이 같은 요청을 다시 보내도 안전하다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final UserReportService userReportService;

    /**
     * 차단하는 사람의 회원 행을 잠가 같은 사람의 차단 요청을 한 줄로 세운다.
     * 버튼을 연타해 같은 차단이 동시에 들어와도 두 번째 요청은 "이미 차단됨"으로 끝난다.
     */
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(UserErrorCode.CANNOT_BLOCK_SELF);
        }
        userRepository.findByIdForUpdate(blockerId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        userRepository.findById(blockedId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return;
        }
        userBlockRepository.save(UserBlock.of(blockerId, blockedId));
        userReportService.reportBlocked(blockerId, blockedId);
    }

    public void unblock(Long blockerId, Long blockedId) {
        userBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    /** 탈퇴한 사람은 목록에서 뺀다(해제할 필요도, 보여줄 정보도 없다). */
    @Transactional(readOnly = true)
    public List<BlockedUserResponse> getBlockedUsers(Long blockerId) {
        List<UserBlock> blocks = userBlockRepository.findByBlockerIdOrderByCreatedAtDescIdDesc(blockerId);
        Map<Long, User> users = userRepository.findAllByIdInAndDeletedAtIsNull(
                        blocks.stream().map(UserBlock::getBlockedId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return blocks.stream()
                .filter(block -> users.containsKey(block.getBlockedId()))
                .map(block -> {
                    User user = users.get(block.getBlockedId());
                    return new BlockedUserResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(),
                            block.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> getBlockedIds(Long blockerId) {
        return userBlockRepository.findBlockedIdsByBlockerId(blockerId);
    }
}
