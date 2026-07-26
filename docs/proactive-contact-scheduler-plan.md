# 선제 연락 스케줄러 작업 계획서

## 1. 목표와 책임 경계

선제 연락의 시각·가능 여부·채널·재시도·중복 방지는 메인 백엔드가 결정한다. AI 서버는 메인 백엔드가 전달한 상황에 맞춰 캐릭터 문구를 생성하고, 감정 상태에 어긋나는 표현을 차단한다.

```text
메인 백엔드
  도래 대상 조회 → 최신 상태 검증 → CHAT/CALL/DEFER 결정
  → 요청 멱등성 보장 → 메시지/통화 저장 → 다음 검사 예약

AI 서버
  contactReason + relationshipState + recentResponse + 캐릭터 snapshot
  → 캐릭터 문구 생성 및 표현 정책 검사
```

스케줄 실행 상태는 새 `ProactiveContactSchedule` 엔티티 하나로 분리한다. 관계·캐릭터·선호 시간·채팅·통화 원본 데이터는 복제하지 않고 기존 `Relationship`, `Character`, `ChatMessage`, `ChatRoom`, `Call`에서 읽는다.

## 2. 현재 코드 기준 확인 사항

- 애플리케이션에 `@EnableScheduling`이 이미 적용돼 있다.
- `CharacterSyncTaskService`에 영속 작업 재시도 패턴과 AI 내부 API 호출 구조가 있다.
- `Character.preferTime`은 다음 세 경로에서 생성·변경된다.
  - `CharacterService.createCharacter`
  - `CharacterService.updateCharacter`
  - `RelationshipService.updateContactPreference`
- `CharacterAiProfile.attachment`는 이미 0~10 점수로 계산된다.
- `Relationship.emotion`과 AI 관계 snapshot이 존재하지만, `NORMAL/UPSET/CONFLICT/REPAIRING`으로 확정 변환하는 메인 백엔드 로직은 아직 없다.
- `ChatRoom.lastMessageAt`은 전체 마지막 메시지 시각일 뿐 마지막 사용자 메시지인지 구분하지 못한다.
- 현재 `ChatMessage`에는 선제 메시지 여부와 request id가 없다.
- 현재 `CallRepository`에는 활성 통화·최근 부재중 전화 조회 메서드가 없다.
- 현재 코드에는 선제 메시지 저장 후 푸시하는 완성된 발송 서비스와 AI 발신 전화 수락 흐름이 없다.

따라서 1차 릴리스는 채팅을 완성하고, 전화는 정책 결과를 바로 발신으로 실행하지 않고 “통화할래?” 채팅으로 안전하게 낮춘다. AI 발신 전화 인프라가 완성된 후 2차로 `CALL` 실행기를 연결한다.

## 3. 데이터 모델: 스케줄 엔티티 1개

### 3.1 `ProactiveContactSchedule`

`Relationship`과 1:1이며 관계당 하나만 존재하도록 unique constraint를 둔다.

| 필드 | 용도 | 초기값 |
|---|---|---|
| `relationship` | 기존 관계 원본 참조 | 생성된 관계 |
| `enabled` | 선제 연락 on/off | `true` 또는 서비스 정책 기본값 |
| `nextCheckAt` | 다음 판단 시각 및 도래 조회 키 | 생성 시 계산 |
| `lastProactiveContactAt` | 마지막 선제 연락 시각 | `null` |
| `consecutiveNoResponseCount` | 연속 미응답 횟수 | `0` |
| `awaitingUserResponse` | 현재 선제 연락의 응답 대기 여부 | `false` |
| `dailyContactCount` | 해당 날짜 발송 성공 횟수 | `0` |
| `dailyCountDate` | 일일 카운트 기준일 | 한국 날짜 |
| `pausedUntil` | 2회 미응답·DND 등에 의한 일시 중지 | `null` |
| `pendingRequestId` | 진행 중 발송의 멱등성 키 | `null` |
| `pendingAction` | 재시도할 `CHAT/CALL` | `null` |
| `pendingAttempts` | 발송 재시도 횟수 | `0` |
| `pendingRetryAt` | 실패 작업의 다음 재시도 시각 | `null` |
| `lastError` | 마지막 실패 원인 | `null` |
| `version` | 스케줄 상태 낙관적 잠금 | JPA 관리 |

실제 Java 필드명은 간결하게 `enabled`, `nextCheckAt`, `pendingRequestId` 등을 사용한다. `lockedUntil`은 추가하지 않는다. 도래 ID를 짧게 조회한 뒤 스케줄 행을 비관적 락으로 다시 읽고 claim만 커밋한다. 외부 AI 호출 중에는 해당 행의 DB lock을 보유하지 않는다.

### 3.2 `ChatMessage`에 추가할 상태

```text
proactiveRequestId VARCHAR(...) NULL UNIQUE
```

선제 메시지만 값을 가진다. 같은 요청의 중복 저장을 DB가 마지막으로 차단하며, 일반 AI 메시지와 선제 AI 메시지를 구분해 최근 반응을 계산할 수 있다. 별도 선제 메시지 엔티티는 만들지 않는다.

### 3.3 인덱스

```text
proactive_contact_schedule(enabled, next_check_at)
proactive_contact_schedule(pending_retry_at)
chat_message(proactive_request_id) UNIQUE
calls(relationship_id, status, created_at)
```

운영 DB에는 명시적 migration SQL을 추가한다. JPA 자동 DDL에 의존하지 않는다.

## 4. 정책을 코드로 고정하는 방법

### 4.1 순수 정책 컴포넌트

`ProactiveContactPolicy`는 DB나 외부 API를 호출하지 않는 순수 계산기로 만든다.

입력:

```text
now, lastContactAt, preferTime, attachment,
relationshipState, recentResponse, consecutiveNoResponseCount,
proactiveEnabled, optedOut, doNotDisturb,
activeChatOrCall, dailyCount, dailyLimit,
busyLikely, agentBusy, callAllowed, repeatedMissedCalls
```

출력:

```text
action: BLOCKED | DEFER | CHAT | CALL
reason
nextCheckAt
contactReason
```

AI ZIP의 `ProactiveSchedulingService` 알고리즘을 메인 백엔드로 옮기되 패키지와 시간 타입을 현재 프로젝트에 맞춘다. 랜덤 생성기는 인터페이스 또는 주입 가능한 `RandomGenerator`로 분리해 테스트를 결정적으로 만든다.

### 4.2 Attachment 구간

0~10 점수 기준은 다음으로 고정한다.

```text
LOW:    attachment < 4
NORMAL: 4 <= attachment < 7
HIGH:   attachment >= 7
```

기본 간격:

```text
LOW 3시간 / NORMAL 2시간 / HIGH 1시간 30분
```

`UPSET`, `REPAIRING`은 1시간을 더하고, `CONFLICT`는 LOW 5~6시간, NORMAL 4~5시간, HIGH 3~4시간 범위에서 뽑는다. 그 외 간격에는 `5/6 ~ 5/4` 배율을 적용하므로 NORMAL 2시간은 1시간 40분~2시간 30분이 된다.

### 4.3 선호 시간대

현재 enum 의미와 호환되는 1차 기준:

```text
MORNING       06:00 <= local time < 12:00
DAY           12:00 <= local time < 18:00
LATE_EVENING  18:00 <= local time
ANYTIME       항상 선호
timezone      Asia/Seoul
```

비선호 시간이면 다음 선호 구간 시작으로 `nextCheckAt`을 이동한다. 현재 enum만으로는 “애매한 시간대”, DND, 평일/주말, 사용자별 timezone을 표현할 수 없으므로 이를 임의 추론하지 않는다. 향후 설정 모델이 생기면 정책 입력만 확장한다.

중요: 현재 주석은 `PreferTime`을 “캐릭터가 선호하는 통화 시간대”라고 설명하지만 실제 요구사항은 “사용자의 연락 선호 시간대”이다. DB 위치는 기존 `Character.preferTime`을 유지하되 주석·API 문서를 사용자 선호 의미로 바로잡는다.

### 4.4 관계 상태

`Relationship.emotion` 및 AI가 반환하는 관계·자기 상태를 한 곳에서 아래 네 값으로 정규화한다.

```text
NORMAL | UPSET | CONFLICT | REPAIRING
```

문자열을 여러 서비스에서 직접 비교하지 않고 `RelationshipStateResolver`가 변환한다. 알 수 없는 값이나 null은 보수적으로 `NORMAL`이 아니라 `UPSET` 또는 채팅 전용 상태로 처리할지 도메인 합의가 필요하다. 1차 구현 기본값은 기존 사용자에게 발송을 갑자기 막지 않도록 `NORMAL`로 하되 전화는 별도 조건을 모두 만족해야만 허용한다.

### 4.5 최근 반응과 미응답

- 마지막 선제 메시지 이후 사용자 메시지가 있으면 미응답 횟수를 0으로 초기화한다.
- 응답 속도와 대화 길이는 우선 명시적인 기준값으로 분류한다.
  - `POSITIVE`: 제한 시간 내 답장 후 사용자 메시지가 일정 개수 이상 이어짐
  - `AMBIGUOUS`: 늦거나 짧은 답장, “바빠/나중에” 신호
  - `NO_RESPONSE`: 다음 판단 시각까지 사용자 답장 없음
- 1회 미응답은 다음 간격을 늘린다.
- 2회 연속 미응답은 다음 선호 시간대로 연기한다.
- 3회 연속 미응답은 `nextCheckAt = null`로 중단한다.
- 이후 사용자가 메시지를 보내면 카운트를 0으로 만들고 새 `nextCheckAt`을 계산해 재개한다.

자연어 “바빠/나중에” 분류는 1차에서 작은 명시적 신호 사전으로 처리하고, LLM 스케줄 판단에는 사용하지 않는다.

## 5. 스케줄 처리 구조

### 5.1 도래 대상 claim

```java
@Scheduled(fixedDelayString = "${proactive.scheduler-delay-ms:60000}")
public void processDueContacts()
```

1. `main = true`, 삭제되지 않은 캐릭터, `nextCheckAt <= now`인 스케줄 ID를 최대 N개 조회한다.
2. ID별 짧은 트랜잭션에서 스케줄 행을 lock한다.
3. 최신 메시지·통화·설정·프로필을 다시 읽어 하드 필터와 정책을 평가한다.
4. `DEFER/BLOCKED`는 다음 시각만 저장한다.
5. `CHAT/CALL`은 UUID request id와 action을 `pending...` 필드에 claim하고 커밋한다.

### 5.2 외부 실행과 완료

1. claim 트랜잭션 밖에서 AI 서버 또는 통화 실행기를 호출한다.
2. AI 호출은 같은 `pendingRequestId`로 재시도한다.
3. 성공 트랜잭션에서 `proactiveRequestId`가 같은 메시지가 없는지 확인하고 AI 메시지를 저장한다.
4. `ChatRoom.lastMessageAt`, 마지막 선제 연락, 일일 카운트, 다음 검사 시각을 함께 갱신한다.
5. pending 필드를 비운다.
6. 실패하면 지수 backoff로 `pendingRetryAt`을 갱신한다.

이 상태 머신은 “AI 응답 성공 후 메인 DB 저장 전에 프로세스가 죽는 경우”에도 같은 request id로 재시도하므로 중복 생성·저장을 막는다.

## 6. 생성·수정·삭제·이벤트 연동

### 6.1 캐릭터 생성

`CharacterService.createCharacter`에서 캐릭터, profile, relationship, chat room을 만든 뒤 같은 트랜잭션 안에서 스케줄을 생성하고 최초 `nextCheckAt`을 설정한다.

```text
request.preferTime
→ Character.preferTime 저장
→ 계산된 attachment 읽기
→ preferTime에 맞춘 최초 후보 시각 계산
→ ProactiveContactSchedule 생성 및 nextCheckAt 저장
```

최초 캐릭터만 `main=true`이므로 비활성 캐릭터에는 시각을 저장해도 worker 대상에서 제외한다. 더 단순하게 하려면 비활성 관계의 시각은 null로 두고 활성화 시 생성한다.

### 6.2 캐릭터 전체 수정

`CharacterService.updateCharacter`에서 trait/profile 재계산 뒤 현재 `preferTime`과 새 attachment로 다음 시각을 재계산한다. profile 재계산과 스케줄 재계산 순서를 보장한다.

```text
preferTime 또는 trait 변경
→ Character/Profile 저장
→ 기존 pending 발송이 없으면 nextCheckAt 재계산
→ AI snapshot UPSERT enqueue
```

이미 claim된 발송이 있으면 오래된 설정으로 보내지 않도록 pending 작업을 취소하고 새 시각을 계산한다.

### 6.3 선호 시간만 변경

`RelationshipService.updateContactPreference`에서 `Character.updatePreferTime` 직후 같은 트랜잭션으로:

```text
pending 발송 취소
→ 변경된 preferTime 기준 nextCheckAt 즉시 재계산
→ AI snapshot UPSERT enqueue
```

즉 생성 API, 전체 수정 API, 선호 시간 수정 API 모두 같은 `ProactiveScheduleCoordinator.reschedule(...)`를 호출해 누락과 계산 중복을 막는다.

### 6.4 활성 캐릭터 변경과 삭제

- 비활성화된 관계는 worker 조회 조건에서 제외하고 pending 작업을 취소한다.
- 활성화된 관계는 최신 메시지 시각과 선호 시간으로 새 검사 시각을 계산한다.
- 삭제 시 `nextCheckAt`과 pending 상태를 null로 만들고 기존 archive/snapshot 삭제 흐름을 따른다.

### 6.5 사용자 채팅 이벤트

현재 조회 전용인 `ChatMessageService`와 별도로 실제 메시지 저장 경로를 하나로 모은다. 사용자 메시지 저장과 같은 트랜잭션 또는 AFTER_COMMIT 이벤트에서:

```text
미응답 횟수 0
paused 상태 해제
pending 선제 발송 취소
마지막 연락 기준으로 nextCheckAt 재계산
```

발송 직전 최신 사용자 메시지를 다시 확인해 “사용자가 방금 답했는데 선제 메시지가 나가는” 경합을 막는다.

### 6.6 통화 이벤트

- `DIALING`, `RINGING`, `IN_PROGRESS`가 있으면 활성 세션 하드 필터를 적용한다.
- AI 발신 `MISSED/REJECTED`를 최근 기간·연속 횟수로 조회한다.
- 통화 완료 또는 사용자 발신 통화는 긍정 반응 후보로 반영하고 다음 시각을 재계산한다.

## 7. AI API 계약 변경

메인 백엔드에 proactive 전용 DTO와 client 메서드를 추가한다.

```http
POST /api/chat/proactive/send
X-Internal-Api-Key: ...
```

```json
{
  "requestId": "proactive-{uuid}",
  "characterId": 10,
  "contactReason": "NORMAL_CHECK_IN",
  "relationshipState": "UPSET",
  "recentResponse": "AMBIGUOUS",
  "history": []
}
```

ZIP의 현재 endpoint는 `ChatRequest`를 받고 request id 멱등성을 이미 사용한다. `contactReason`, `relationshipState`, `recentResponse`를 DTO/prompt에 명시적으로 추가하되 `ProactiveContactPolicyService` 표현 안전장치는 AI 서버에 남긴다. AI ZIP의 `ProactiveSchedulingService`, `PreferredContactTimePolicy`, `ScheduleContextFactory`는 메인 이전 완료 후 제거한다.

## 8. 구현 순서

### 단계 0 — 정책 상수 확정

- 일일 한도 기본값
- 최소 쿨타임
- 첫 선제 연락 허용 시점
- `preferTime` 실제 구간과 timezone
- 선제 연락 기본 on/off
- 답장 속도·대화 길이 분류 기준

### 단계 1 — 순수 정책과 테스트

- enum/value object 및 `ProactiveContactPolicy`
- `PreferredContactTimePolicy`
- attachment 구간, 상태·반응 보정, 랜덤 경계 테스트
- 하드 필터 우선순위와 1/2/3회 미응답 테스트

### 단계 2 — DB 상태와 조회

- `Relationship`, `ChatMessage` 최소 컬럼과 migration
- due 조회, 관계 lock, 마지막 사용자/AI/선제 메시지 조회
- 활성 통화·최근 missed call 조회

### 단계 3 — lifecycle 연동

- 생성/전체 수정/선호 시간 수정/활성화/삭제 reschedule
- 사용자 메시지 및 통화 이벤트 reschedule
- 세 경로에서 `preferTime` 변경 반영 통합 테스트

### 단계 4 — 채팅 worker

- claim/execute/complete/retry 상태 머신
- AI proactive client DTO
- AI 응답 메시지 저장과 chat room 갱신
- 중복 request id 및 프로세스 재시작 복구 테스트

### 단계 5 — 운영 발송

- FCM 등 실제 푸시가 존재하면 메시지 커밋 이후 연결
- metrics/log: action, reason, interval, retry, duplicate prevention
- feature flag와 소수 사용자 rollout

### 단계 6 — 전화

- AI 발신 전화 생성·수락·timeout·missed 전이 완성
- CALL 실행기 연결
- 그 전까지 CALL 후보는 “지금 잠깐 통화할래?” 선제 채팅으로 downgrade

## 9. 테스트 완료 기준

- 생성 요청의 모든 `PreferTime` 값이 최초 검사 시각에 반영된다.
- 전체 캐릭터 수정과 연락 선호 수정 직후 예전 시간의 pending 발송이 취소된다.
- 비선호 시간에는 발송하지 않고 정확한 다음 선호 구간으로 이동한다.
- attachment 경계값 4, 7과 모든 관계 상태 조합의 간격이 정책 범위 안이다.
- DND/opt-out/비활성/삭제/활성 세션/쿨타임/일일 한도는 다른 계산보다 먼저 차단된다.
- 연속 미응답 1/2/3회 정책과 사용자 답장 후 재개가 동작한다.
- worker 두 인스턴스가 같은 관계를 동시에 처리해도 AI 호출·메시지 저장은 한 번이다.
- AI timeout, 5xx, 성공 직후 프로세스 종료를 재현해도 같은 request id로 복구된다.
- 선제 메시지 저장과 다음 검사 시각·일일 카운트 갱신이 원자적으로 완료된다.

## 10. 범위 밖 또는 선행 결정이 필요한 항목

현재 모델에는 다음 정보가 없으므로 1차 구현에서 가짜 값으로 채우지 않는다.

- 사용자별 DND와 명시적 opt-out 저장 위치
- 채팅 “현재 열어 둠” 또는 WebSocket 세션 상태
- 사용자 timezone
- 대화에서 추출한 자유 형식 선호 시간
- 캐릭터의 수업/업무 실시간 상태
- 선제 전화 허용 설정과 실제 AI 발신 전화 연결
- 푸시 토큰 및 발송 성공 상태

해당 기능은 설정/세션 인프라가 제공될 때 하드 필터 입력 포트로 추가한다. 스케줄러 정책 자체는 이 입력들이 추가돼도 변경하지 않도록 구성한다.
