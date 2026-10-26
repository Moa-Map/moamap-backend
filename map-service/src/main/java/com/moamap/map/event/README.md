# 회원가입 시 "나만의 지도" 자동 생성

가입한 사용자에게 지도를 하나 만들어주는 기능이다. 회원은 user-service, 지도는 map-service에 있어
두 서비스가 메시지로 연결된다. 이 문서는 그 흐름과 그렇게 만든 이유를 설명한다.

---

## 1. 무엇이 만들어지나

첫 로그인(회원가입)을 하면 **"나만의 지도"** 라는 지도가 자동으로 하나 생긴다.

| | 나만의 지도 | 일반 프라이빗 지도 |
|---|---|---|
| 생성 | 가입 시 자동 | 사용자가 직접 |
| 용도 | 혼자 쓰는 지도 | 친구·지인과 함께 |
| 초대 | 불가 (초대 코드를 발급하지 않음) | 초대 코드로 합류 |
| 개수 | 사용자당 하나 | 제한 없음 |
| 삭제 | 불가 (`MAP_016`) | 가능 |
| 장소 등록 | 일반 프라이빗과 동일 | — |

응답에서는 `personal` 값으로 구분한다.

```json
{
  "id": 1, "name": "나만의 지도", "type": "PRIVATE",
  "memberCount": 1, "placeCount": 0, "joined": true,
  "personal": true
}
```

`GET /api/v1/maps/me?type=PRIVATE` 로 함께 내려오므로, 클라이언트는 `personal` 로
`나만의 지도` 섹션과 나머지를 나누면 된다. **새 API는 추가되지 않았다.**

---

## 2. 전체 흐름

```
[user-service]                                    [RabbitMQ]           [map-service]

카카오 첫 로그인
 ┌─ 트랜잭션 ──────────────┐
 │ INSERT users           │
 │ INSERT outbox_event    │   ← 회원과 이벤트를 함께 저장
 └──────── COMMIT ────────┘
                                user.events
 [OutboxPublisher · 2초마다]  ──익스체인지──▶  map-service.user-registered 큐
   미발행 건 조회 → 발행                              │  (실패 3회 → DLQ)
   → published_at 기록                                ▼
                                        UserRegisteredEventListener
                                          → 나만의 지도 생성 (멱등)
```

가입 응답은 이벤트를 기다리지 않는다. 지도는 보통 **2초 안쪽**에 만들어진다.

---

## 3. 왜 이렇게 만들었나

### 왜 MapType에 값을 더하지 않았나

`MapType.PERSONAL` 을 새로 만드는 쪽이 의미상 깔끔해 보이지만, **place-service가 깨진다.**

place-service에는 `MapType` 을 미러링한 enum(`OFFICIAL/COMMUNITY/PRIVATE`)이 있고,
`MapClient` 응답을 역직렬화해 장소 승인 상태·사진 발급 권한·활동내역 범위 등 여러 곳에서 판단한다.
새 값이 오면 역직렬화가 실패하고, **두 서비스를 반드시 함께 배포해야 하는 결합**이 생긴다.

그래서 타입은 `PRIVATE` 그대로 두고 `personal` 플래그로 구분했다.
place-service 입장에서는 그냥 프라이빗 지도라 **한 줄도 바뀌지 않았다.**
"혼자 쓰는" 제약은 map-service 안에서만 건다.

### 왜 Outbox를 썼나

커밋 직후에 곧바로 브로커로 보내면 이런 구간이 생긴다.

```
INSERT users → COMMIT ─┬─ (여기서 브로커가 죽거나 서버가 재시작되면)
                       └─ 발행 실패 → 이벤트 소실
```

회원은 저장됐는데 이벤트만 사라진다. 커밋은 이미 끝나서 되돌릴 수도 없다.

`publisher-confirm` 을 켜도 이건 막지 못한다. 그건 "브로커가 잘 받았다"는 확인일 뿐,
**발행을 시도조차 못 한 경우**는 알 방법이 없기 때문이다.

같은 서비스의 장소 개수 이벤트는 이런 위험을 감수해도 된다.
절대값을 보내므로 **다음 변경 때 자연히 복구**되기 때문이다.
하지만 가입은 **평생 한 번뿐**이라 놓치면 그 사용자는 영원히 지도가 없다. 복구 경로가 없다.

Outbox는 이벤트를 브로커가 아니라 **DB에 회원과 같은 트랜잭션으로** 먼저 쓴다.
커밋됐다면 이벤트도 반드시 남아 있고, 발행 실패는 폴러가 다음 주기에 다시 시도한다.

### 왜 멱등성이 필요한가

RabbitMQ는 메시지를 **최소 한 번** 배달한다. 네트워크 문제로 같은 메시지가 다시 올 수 있고,
폴러가 발행 후 표시 직전에 죽어도 같은 이벤트가 두 번 나간다.

장소 개수는 절대값이라 두 번 처리해도 결과가 같지만, **지도 생성은 두 번 처리하면 지도가 두 개 생긴다.**

그래서 두 겹으로 막는다.

```java
if (mapRepository.existsByOwnerIdAndPersonalIsTrue(userId)) {
    return;                                   // ① 흔한 중복을 싸게 거른다
}
try {
    // 생성
} catch (DataIntegrityViolationException e) {
    // ② 동시에 처리된 경우 — DB 제약이 막아준다
}
```

①만으로는 **동시에 두 건이 처리될 때 둘 다 통과**한다. 조회와 저장 사이에 틈이 있기 때문이다.
그래서 DB 유니크 인덱스를 최종 방어선으로 둔다.

```sql
CREATE UNIQUE INDEX uk_map_personal_owner
    ON map_entity (owner_id) WHERE personal = TRUE;
```

`WHERE personal = TRUE` 를 붙인 **부분 인덱스**라, 나만의 지도만 하나로 제한되고
일반 지도는 몇 개든 만들 수 있다.

### 왜 리스너에서 예외를 잡지 않나

```java
@RabbitListener(queues = "map-service.user-registered")
public void handle(UserRegisteredEvent event) {
    mapService.createPersonalMapIfAbsent(event.userId(), personalMapName);
}
```

여기서 `try-catch` 로 감싸면 **메시지가 성공 처리돼 사라진다.**
예외를 그대로 올려보내야 재시도(3회)와 DLQ 라우팅이 동작한다.

---

## 4. 추가·변경된 것

### user-service (신규)
```
outbox/OutboxEvent            보관함 엔티티
outbox/OutboxEventRepository  미발행 조회(잠긴 행 건너뜀) · 오래된 기록 삭제
outbox/OutboxRecorder         트랜잭션 안에서 이벤트 기록 (MANDATORY)
outbox/OutboxPublisher        2초마다 발행 · 매일 04시 정리
event/UserRegisteredEvent     발행 payload
config/RabbitConfig           user.events 익스체인지 선언
```

### user-service (수정)
| 파일 | 변경 |
|---|---|
| `build.gradle` | `spring-boot-starter-amqp` 추가 |
| `application.yml` | `spring.rabbitmq.*`, `outbox.*` |
| `UserServiceApplication` | `@EnableScheduling` |
| `AuthService` | 신규 가입일 때 이벤트 기록 (3줄) |

### map-service (신규)
```
event/UserRegisteredEvent          수신 payload (발행자 클래스에 의존하지 않음)
event/UserRegisteredEventListener  수신 → 지도 생성
```

### map-service (수정)
| 파일 | 변경 |
|---|---|
| `MapEntity` | `personal` 필드 + `createPersonal()` |
| `MapEntityRepository` | `existsByOwnerIdAndPersonalIsTrue` |
| `MapService` | `createPersonalMapIfAbsent()`, 삭제 차단 |
| `MapErrorCode` | `MAP_016` |
| `MapSummaryResponse` · `MapDetailResponse` | `personal` 노출 |
| `RabbitConfig` | `user.registered` 큐·DLQ·바인딩 |
| `application.yml` | `map.personal-map.name` |
| `db/init.sql` | 부분 유니크 인덱스 |

**place-service는 변경 없음.**

---

## 5. 설정

```yaml
# user-service
outbox:
  poll-interval-ms: 2000   # 이 값이 가입 후 지도가 생기기까지의 지연을 좌우한다
  retention-days: 7        # 발행 완료 기록 보관 기간

# map-service
map:
  personal-map:
    name: 나만의 지도       # 문구 변경 시 재배포 불필요
```

RabbitMQ 접속 정보는 장소 개수 기능에서 쓰던 환경변수(`RABBITMQ_*`)를 그대로 쓴다.
**새로 추가된 인프라는 없다.**

---

## 6. 로컬에서 확인하기

```bash
docker compose up -d postgres rabbitmq
./gradlew :map-service:bootRun
./gradlew :user-service:bootRun
```

실제 가입은 카카오 토큰이 필요하므로, 보관함에 이벤트를 직접 넣어 흐름을 확인할 수 있다.

```sql
INSERT INTO user_service.outbox_event (aggregate_id, event_type, payload, created_at)
VALUES ('999', 'user.registered',
        '{"eventId":"test-1","userId":999,"occurredAt":"2026-07-29T00:00:00Z"}', now());
```

2초쯤 뒤 지도가 만들어진다.

```sql
SELECT id, name, type, owner_id, personal, invite_code FROM map_service.map_entity WHERE owner_id = 999;
SELECT (published_at IS NOT NULL) AS published FROM user_service.outbox_event;
```

```bash
curl 'http://localhost:8083/api/v1/maps/me?type=PRIVATE' -H 'X-User-Id: 999'
curl -X DELETE 'http://localhost:8083/api/v1/maps/1' -H 'X-User-Id: 999'   # MAP_016
```

---

## 7. 알아두면 좋은 것

### 기존 가입자
이벤트는 **앞으로 가입하는 사람**에게만 발행된다. 이미 가입한 사용자는 지도가 생기지 않는다.
DB를 초기화할 예정이라 이번에는 채워 넣지 않았다. 필요해지면 일회성 스크립트로 넣으면 된다.

### 브로커가 죽어 있으면
가입은 정상적으로 끝나고 이벤트는 보관함에 남는다. 브로커가 살아나면 폴러가 발행해 지도가 만들어진다.
**가입이 막히지는 않는다.**

### 보관함이 무한정 커지지 않게
발행이 끝난 기록은 7일 뒤 정리 배치가 지운다. 미발행 건만 담는 부분 인덱스를 써서,
기록이 쌓여도 폴러 조회는 계속 빠르다.

### 다중 인스턴스
폴러가 여러 개 동시에 돌아도 잠긴 행을 건너뛰므로 같은 이벤트를 두 서버가 집지 않는다.
별도의 분산 락은 두지 않았다.

---

## 8. 다음에 할 수 있는 것

| 항목 | 내용 |
|---|---|
| 장소 개수 이벤트도 Outbox로 | 지금은 발행 실패를 로그만 남기고 넘어간다. 같은 방식으로 옮기면 유실이 사라진다 |
| DLQ 모니터링 | 쌓인 메시지를 알아채고 다시 넣는 수단이 아직 없다 |
| 정합성 보정 | "지도 없는 사용자"를 주기적으로 찾아 채우면, 이벤트가 어떤 이유로 빠져도 스스로 복구된다 |
| 다른 소비자 추가 | 환영 알림·가입 통계 등은 `user.registered` 를 구독하기만 하면 된다. **발행자는 손대지 않는다** |
