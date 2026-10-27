package com.moamap.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.moamap.map.dto.MapLeaveResponse;
import com.moamap.map.user.UserClient;
import com.moamap.map.user.dto.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapLeaveServiceTest {

    private static final Long MAP_ID = 1L;
    private static final Long LEAVER = 10L;
    private static final Long NEW_OWNER = 20L;

    @Mock private MapService mapService;
    @Mock private UserClient userClient;
    @InjectMocks private MapLeaveService mapLeaveService;

    @Test
    void 방장이_나가면_새_방장의_닉네임을_담는다() {
        given(mapService.leave(MAP_ID, LEAVER, NEW_OWNER)).willReturn(Optional.of(NEW_OWNER));
        given(userClient.findProfiles(List.of(NEW_OWNER)))
            .willReturn(Map.of(NEW_OWNER, new UserProfileResponse(NEW_OWNER, "모아", null)));

        MapLeaveResponse response = mapLeaveService.leave(MAP_ID, LEAVER, NEW_OWNER);

        assertThat(response.newOwner()).isEqualTo(new MapLeaveResponse.NewOwner(NEW_OWNER, "모아"));
    }

    @Test
    void 회원_정보를_가져오지_못해도_나가기는_성공하고_닉네임만_비운다() {
        given(mapService.leave(MAP_ID, LEAVER, NEW_OWNER)).willReturn(Optional.of(NEW_OWNER));
        given(userClient.findProfiles(List.of(NEW_OWNER))).willReturn(Map.of());

        MapLeaveResponse response = mapLeaveService.leave(MAP_ID, LEAVER, NEW_OWNER);

        assertThat(response.newOwner()).isEqualTo(new MapLeaveResponse.NewOwner(NEW_OWNER, null));
    }

    @Test
    void 방장이_아니면_새_방장이_없고_회원_정보도_조회하지_않는다() {
        given(mapService.leave(MAP_ID, LEAVER, null)).willReturn(Optional.empty());

        assertThat(mapLeaveService.leave(MAP_ID, LEAVER, null).newOwner()).isNull();
        verifyNoInteractions(userClient);
    }
}
