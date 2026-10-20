package com.moamap.notification.dto;

import com.moamap.notification.entity.Notification;
import com.moamap.notification.entity.NotificationTargetType;
import com.moamap.notification.entity.NotificationType;
import com.moamap.notification.user.UserProfiles;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        @Schema(description = "화면에 보여줄 문구", example = "모아님이 내 장소에 댓글을 남겼어요.")
        String message,
        @Schema(description = "알림을 일으킨 사람. 보여주지 않는 종류(승인·반려·문의 답변 등)는 비어 있다.")
        ActorResponse actor,
        @Schema(description = "알림을 눌렀을 때 열 대상의 종류")
        NotificationTargetType targetType,
        Long targetId,
        @Schema(description = "대상이 속한 지도. 없으면 비어 있다.")
        Long mapId,
        boolean read,
        Instant createdAt
) {

    /** 문구에 넣을 닉네임은 행동한 사람의 프로필에서 가져온다. 모르면 닉네임 없는 문구가 된다. */
    public static NotificationResponse of(Notification notification, UserProfiles profiles) {
        ActorResponse actor = ActorResponse.resolve(notification.getActorId(), profiles);
        String nickname = actor == null ? null : actor.nickname();
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getType().messageFor(nickname),
                actor,
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getMapId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
