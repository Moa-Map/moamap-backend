package com.moamap.user.report.dto;

import com.moamap.user.report.entity.ReportReason;
import com.moamap.user.report.entity.UserReport;
import com.moamap.user.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 운영자가 보는 사용자 신고 한 건. 신고된 프로필 내용을 함께 담아 따로 조회하지 않고 판단할 수 있게 한다.
 */
public record UserReportAdminResponse(
        Long reportId,
        Long reporterId,
        ReportReason reason,
        String detail,
        Instant reportedAt,
        @Schema(description = "이 사용자에 대해 쌓인 신고 수")
        long targetReportCount,
        Target target
) {

    public record Target(
            Long userId,
            @Schema(description = "탈퇴한 회원이면 true. 이때 프로필 정보는 이미 지워져 있다.")
            boolean withdrawn,
            String nickname,
            String profileImageUrl,
            String introduction
    ) {

        static Target from(Long userId, User user) {
            if (user == null || user.isWithdrawn()) {
                return new Target(userId, true, null, null, null);
            }
            return new Target(userId, false, user.getNickname(), user.getProfileImageUrl(), user.getIntroduction());
        }
    }

    public static UserReportAdminResponse of(UserReport report, User target, long targetReportCount) {
        return new UserReportAdminResponse(report.getId(), report.getReporterId(), report.getReason(),
                report.getDetail(), report.getCreatedAt(), targetReportCount,
                Target.from(report.getTargetUserId(), target));
    }
}
