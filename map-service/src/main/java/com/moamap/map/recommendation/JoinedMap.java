package com.moamap.map.recommendation;

import java.time.LocalDateTime;
import java.util.List;
import com.moamap.map.entity.MapRole;

/**
 * 관심 프로필을 만드는 재료. 사용자가 참여 중인 지도 한 건을 나타낸다.
 *
 * 커뮤니티·프라이빗을 가리지 않고 모두 신호로 쓴다. 프라이빗 지도의 취향도 관심사에 반영하되,
 * 추천 결과로는 커뮤니티 지도만 내보내므로 비공개 정보가 노출되지 않는다.
 */
public record JoinedMap(List<String> tags, MapRole role, LocalDateTime joinedAt) {
}
