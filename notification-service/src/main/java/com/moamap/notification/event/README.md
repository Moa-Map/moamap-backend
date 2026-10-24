# 알림 이벤트 약속

알림을 만들고 싶은 서비스가 지켜야 할 것만 적는다. 받는 쪽 구현은 `ActivityEventListener`, `NotificationRecorder` 참고.

---

## 1. 흐름

```
[place / map / user-service]                [RabbitMQ]                     [notification-service]

 일이 일어남 (댓글 작성 등)
 커밋 후 자기 익스체인지로 발행  ──▶  place.events / map.events / user.events
                                          │  라우팅 키로 바인딩
                                          ▼
                                 notification-service.activity 큐  ──▶  알림 저장 (중복·본인 행동 거름)
                                          │  실패 3회
                                          ▼
                                 notification-service.activity.dlq
```

발행하는 서비스는 **알림을 모른다.** "무슨 일이 있었는지"를 자기 익스체인지에 보내기만 한다.
알림 서비스가 멈춰 있어도 메시지는 큐에 쌓였다가 다시 뜰 때 처리된다.

---

## 2. 라우팅 키

| 라우팅 키 | 익스체인지 | 알림 | recipientId | actorId | targetId | mapId |
|---|---|---|---|---|---|---|
| `place.approved` | `place.events` | 장소 승인 | 장소 등록자 | (저장 안 함) | 장소 | 지도 |
| `place.rejected` | `place.events` | 장소 반려 | 장소 등록자 | (저장 안 함) | 장소 | 지도 |
| `place.commented` | `place.events` | 내 장소에 댓글 | 장소 등록자 | 댓글 작성자 | 장소 | 지도 |
| `map.post.commented` | `map.events` | 내 게시글에 댓글 | 게시글 작성자 | 댓글 작성자 | 게시글 | 지도 |
| `map.admin.assigned` | `map.events` | 지도 관리자로 지정됨 | 지정된 멤버 | 지정한 사람 | 지도 | 지도 |
| `map.owner.transferred` | `map.events` | 방장 승계 (이전 방장이 나가거나 탈퇴) | 새 방장 | (저장 안 함) | 지도 | 지도 |
| `inquiry.answered` | `user.events` | 문의 답변 완료 | 문의자 | (저장 안 함) | 문의 | — |
| `place.reported` | `place.events` | 내 지도의 장소가 신고됨 (그 장소의 첫 신고) | 방장·관리자 | (저장 안 함, 신고자 비공개) | 장소 | 지도 |
| `place.report.received` | `place.events` | 새 신고 (장소·장소 댓글) | 운영자 | (저장 안 함) | 신고 | 지도 |
| `map.report.received` | `map.events` | 새 신고 (지도·로그·로그 댓글) | 운영자 | (저장 안 함) | 신고 | 지도 |
| `user.report.received` | `user.events` | 새 신고 (사용자) | 운영자 | (저장 안 함) | 신고 | — |

목록은 `ActivityRoute`가 정본이다. 새 알림은 `NotificationType`과 `ActivityRoute`에 한 줄씩 더한다.

---

## 3. 본문

모든 라우팅 키가 같은 모양을 쓴다(`ActivityEvent`). 클래스를 공유하지 않으므로 필드 이름만 맞춘다.

```json
{
  "eventId": "3f2c9c1e-6a51-4c43-9a8a-0b8f3c3c2e11",
  "recipientId": 12,
  "actorId": 34,
  "targetId": 56,
  "mapId": 7,
  "occurredAt": "2026-10-20T03:00:00Z"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `eventId` | ✅ | 이벤트마다 새로 만든 값(UUID). 64자 이하. **같은 이벤트를 다시 보낼 때는 같은 값** |
| `recipientId` | ✅ | 알림을 받을 회원 |
| `actorId` | | 그 일을 한 회원. `recipientId`와 같으면 알림을 만들지 않는다 |
| `targetId` | ✅ | 알림을 눌렀을 때 열 대상 |
| `mapId` | | 대상이 속한 지도 |
| `occurredAt` | | 일어난 시각. 없으면 받은 시각 |

**닉네임·장소 이름 같은 문구는 보내지 않는다.** 알림에는 ID만 저장하고, 닉네임은 조회할 때 붙인다.

---

## 4. 발행하는 쪽 체크리스트

- [ ] 트랜잭션 **커밋 후** 발행한다. 롤백된 댓글에 알림이 가면 안 된다
- [ ] 익스체인지는 `durable` topic, `autoDelete=false`로 선언한다(받는 쪽과 속성이 다르면 선언이 실패한다)
- [ ] 놓치면 안 되는 알림이면 user-service처럼 Outbox를 쓴다. 놓쳐도 되면 커밋 후 바로 발행(best-effort)
- [ ] **notification-service가 먼저 배포돼 큐가 만들어진 뒤** 발행을 켠다. 큐가 없을 때 보낸 메시지는 버려진다

---

## 5. 그 밖의 처리

| 일 | 처리 |
|---|---|
| 회원 탈퇴(`user.withdrawn`) | 그 회원이 **받은** 알림 삭제. 그 회원이 일으킨 알림은 남고 "탈퇴한 사용자"로 보인다 |
| 보관 기간 | 90일 지난 알림은 매일 04:30(한국 시간) 삭제 (`notification.retention-days`, `cleanup-cron`) |
| 같은 이벤트 재수신 | `source_event_id` 유일 제약으로 한 번만 저장 |
| 형식이 잘못된 이벤트 | 저장하지 않고 재시도 후 DLQ |
