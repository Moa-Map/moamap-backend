package com.moamap.notification.user;

import com.moamap.notification.user.dto.UserProfileResponse;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * user-service에서 가져온 프로필 묶음.
 *
 * user-service는 없거나 탈퇴한 회원을 응답에서 뺀다. 그래서 "조회에 실패했다"와 "그 회원이 없다"를 구분해야
 * 정상 회원을 탈퇴한 사용자로 잘못 보여주지 않는다.
 */
public final class UserProfiles {

    private static final UserProfiles UNAVAILABLE = new UserProfiles(null);

    /** null이면 조회에 실패한 것이다. */
    private final Map<Long, UserProfileResponse> byId;

    private UserProfiles(Map<Long, UserProfileResponse> byId) {
        this.byId = byId;
    }

    public static UserProfiles of(Collection<UserProfileResponse> profiles) {
        return new UserProfiles(profiles.stream()
                .collect(Collectors.toUnmodifiableMap(UserProfileResponse::id, Function.identity(),
                        (first, second) -> first)));
    }

    public static UserProfiles unavailable() {
        return UNAVAILABLE;
    }

    public boolean isAvailable() {
        return byId != null;
    }

    /** 조회에 성공했는데 비어 있으면 없거나 탈퇴한 회원이다. 조회에 실패했을 때는 부르기 전에 isAvailable로 확인한다. */
    public Optional<UserProfileResponse> find(Long userId) {
        return isAvailable() ? Optional.ofNullable(byId.get(userId)) : Optional.empty();
    }
}
