package com.moamap.notification.entity;

import lombok.Getter;

/**
 * 알림 종류와 표시 문구.
 *
 * 문구를 저장하지 않고 조회할 때 만든다. 저장해 두면 행동한 사람이 탈퇴해도 남의 알림에 닉네임이 남고,
 * 문구를 고칠 때 이미 쌓인 알림은 바뀌지 않는다.
 *
 * actorFormat이 없는 종류는 행동한 사람을 보여주지 않는다(시스템·관리자 처리 결과라 사람이 의미가 없거나,
 * 관리자 ID처럼 사용자에게 알릴 필요가 없는 정보다). 이런 알림은 행동한 사람 ID도 저장하지 않는다.
 */
@Getter
public enum NotificationType {

    PLACE_APPROVED(NotificationTargetType.PLACE, null, "등록 요청한 장소가 승인됐어요."),
    PLACE_REJECTED(NotificationTargetType.PLACE, null, "등록 요청한 장소가 반려됐어요."),
    PLACE_COMMENTED(NotificationTargetType.PLACE, "%s님이 내 장소에 댓글을 남겼어요.", "내 장소에 새 댓글이 달렸어요."),
    MAP_POST_COMMENTED(NotificationTargetType.MAP_POST, "%s님이 내 게시글에 댓글을 남겼어요.", "내 게시글에 새 댓글이 달렸어요."),
    MAP_ADMIN_ASSIGNED(NotificationTargetType.MAP, "%s님이 나를 지도 관리자로 지정했어요.", "지도 관리자로 지정됐어요."),
    MAP_OWNER_TRANSFERRED(NotificationTargetType.MAP, null, "지도의 새 방장이 됐어요. 이전 방장이 지도를 떠났어요."),
    INQUIRY_ANSWERED(NotificationTargetType.INQUIRY, null, "문의하신 내용에 답변이 완료됐어요.");

    private final NotificationTargetType targetType;
    /** 행동한 사람의 닉네임을 넣는 문구. null이면 행동한 사람을 보여주지 않는 종류다. */
    private final String actorFormat;
    /** 행동한 사람이 없거나, 닉네임을 가져오지 못했을 때 쓰는 문구. */
    private final String defaultMessage;

    NotificationType(NotificationTargetType targetType, String actorFormat, String defaultMessage) {
        this.targetType = targetType;
        this.actorFormat = actorFormat;
        this.defaultMessage = defaultMessage;
    }

    public boolean showsActor() {
        return actorFormat != null;
    }

    /** 닉네임을 알면 닉네임을 넣은 문구, 모르면 기본 문구. */
    public String messageFor(String actorNickname) {
        if (!showsActor() || actorNickname == null) {
            return defaultMessage;
        }
        return actorFormat.formatted(actorNickname);
    }
}
