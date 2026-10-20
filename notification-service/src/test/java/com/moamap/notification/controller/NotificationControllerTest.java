package com.moamap.notification.controller;

import static com.moamap.notification.support.NotificationFixtures.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.common.exception.BusinessException;
import com.moamap.notification.dto.ActorResponse;
import com.moamap.notification.dto.NotificationResponse;
import com.moamap.notification.dto.UnreadCountResponse;
import com.moamap.notification.entity.NotificationTargetType;
import com.moamap.notification.entity.NotificationType;
import com.moamap.notification.exception.NotificationErrorCode;
import com.moamap.notification.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void 내_알림_목록을_페이지로_돌려준다() throws Exception {
        NotificationResponse response = new NotificationResponse(5L, NotificationType.PLACE_COMMENTED,
                "모아님이 내 장소에 댓글을 남겼어요.", new ActorResponse(3L, "모아", null),
                NotificationTargetType.PLACE, 10L, 20L, false, NOW);
        given(notificationService.getNotifications(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications").header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(5))
                .andExpect(jsonPath("$.data.content[0].type").value("PLACE_COMMENTED"))
                .andExpect(jsonPath("$.data.content[0].message").value("모아님이 내 장소에 댓글을 남겼어요."))
                .andExpect(jsonPath("$.data.content[0].actor.nickname").value("모아"))
                .andExpect(jsonPath("$.data.content[0].targetType").value("PLACE"))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void 안_읽은_알림_수를_돌려준다() throws Exception {
        given(notificationService.getUnreadCount(1L)).willReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/v1/notifications/unread-count").header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    void 알림을_읽음으로_바꾼다() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/5/read").header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markRead(1L, 5L);
    }

    @Test
    void 모두_읽음으로_바꾼다() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all").header(USER_ID_HEADER, 1))
                .andExpect(status().isOk());

        verify(notificationService).markAllRead(1L);
    }

    @Test
    void 알림을_지운다() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/5").header(USER_ID_HEADER, 1))
                .andExpect(status().isOk());

        verify(notificationService).delete(1L, 5L);
    }

    @Test
    void 없거나_남의_알림은_404다() throws Exception {
        willThrow(new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationService).delete(1L, 5L);

        mockMvc.perform(delete("/api/v1/notifications/5").header(USER_ID_HEADER, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_001"));
    }

    @Test
    void 인증_헤더가_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_005"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void 알림_ID가_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/abc/read").header(USER_ID_HEADER, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 없는_경로는_서버_오류가_아니라_404다() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/5/unknown").header(USER_ID_HEADER, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON_003"));
    }

    @Test
    void 허용되지_않은_메서드는_405다() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/read-all").header(USER_ID_HEADER, 1))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("COMMON_002"));
    }
}
