package com.moamap.map.internal;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import com.moamap.map.entity.MapMember;
import com.moamap.map.entity.MapRole;
import com.moamap.map.repository.MapMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalMapController.class)
class InternalMapControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MapMemberRepository mapMemberRepository;

    @Test
    void 방장과_관리자만_돌려준다() throws Exception {
        given(mapMemberRepository.findByMapId(5L)).willReturn(List.of(
            MapMember.of(5L, 1L, MapRole.OWNER),
            MapMember.of(5L, 2L, MapRole.ADMIN),
            MapMember.of(5L, 3L, MapRole.MEMBER)));

        mockMvc.perform(get("/internal/maps/5/managers"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0]").value(1))
            .andExpect(jsonPath("$.data[1]").value(2));
    }
}
