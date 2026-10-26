package com.moamap.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationTypeTest {

    @Test
    void 닉네임을_알면_닉네임을_넣은_문구를_쓴다() {
        assertThat(NotificationType.MAP_ADMIN_ASSIGNED.messageFor("모아")).isEqualTo("모아님이 나를 지도 관리자로 지정했어요.");
    }

    @Test
    void 닉네임에_서식_문자가_있어도_그대로_보인다() {
        assertThat(NotificationType.PLACE_COMMENTED.messageFor("100%s")).isEqualTo("100%s님이 내 장소에 댓글을 남겼어요.");
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void 닉네임을_모르면_기본_문구를_쓴다(NotificationType type) {
        assertThat(type.messageFor(null)).isEqualTo(type.getDefaultMessage()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"PLACE_APPROVED", "PLACE_REJECTED", "MAP_OWNER_TRANSFERRED", "INQUIRY_ANSWERED", "PLACE_REPORTED", "REPORT_RECEIVED"})
    void 행동한_사람을_보여주지_않는_종류는_닉네임이_있어도_기본_문구다(NotificationType type) {
        assertThat(type.showsActor()).isFalse();
        assertThat(type.messageFor("모아")).isEqualTo(type.getDefaultMessage());
    }
}
