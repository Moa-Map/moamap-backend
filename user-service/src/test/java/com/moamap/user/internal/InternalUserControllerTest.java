package com.moamap.user.internal;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moamap.user.user.entity.Role;
import com.moamap.user.user.entity.User;
import com.moamap.user.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalUserController.class)
class InternalUserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserRepository userRepository;

    @Test
    void 운영자면_true다() throws Exception {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(Role.ADMIN, false)));

        mockMvc.perform(get("/internal/users/1/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admin").value(true));
    }

    @Test
    void 일반_회원이거나_탈퇴한_운영자거나_없는_회원이면_false다() throws Exception {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(Role.USER, false)));
        given(userRepository.findById(2L)).willReturn(Optional.of(user(Role.ADMIN, true)));
        given(userRepository.findById(3L)).willReturn(Optional.empty());

        for (long id = 1; id <= 3; id++) {
            mockMvc.perform(get("/internal/users/{id}/admin", id))
                    .andExpect(jsonPath("$.data.admin").value(false));
        }
    }

    @Test
    void 운영자_ID_목록을_준다() throws Exception {
        given(userRepository.findIdsByRole(Role.ADMIN)).willReturn(List.of(7L, 8L));

        mockMvc.perform(get("/internal/users/admins"))
                .andExpect(jsonPath("$.data[0]").value(7))
                .andExpect(jsonPath("$.data[1]").value(8));
    }

    private static User user(Role role, boolean withdrawn) {
        User user = User.createSocialUser("kakao", "p" + role + withdrawn, "닉네임", null, null);
        ReflectionTestUtils.setField(user, "role", role);
        if (withdrawn) {
            user.withdraw(Instant.now());
        }
        return user;
    }
}
