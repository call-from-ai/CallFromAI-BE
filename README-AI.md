# 전화왔어(Love Call) — 프로젝트 개발 가이드

> 이 문서는 프로젝트에 참여하는 모든 개발자 및 AI 협업 도구가
> 프로젝트의 전체 구조·표준·담당 경계를 정확히 이해하고
> 일관되게 구현하기 위한 기준 문서다.
> 특정 도메인에 치우치지 않으며, 모든 도메인을 동등하게 다룬다.

---

## 1. 프로젝트 개요

**전화왔어(Love Call)** 는 AI가 사용자에게 능동적으로 먼저 전화·메시지를 거는
AI 데이팅 콜 시뮬레이션 앱이다. 사용자는 여러 캐릭터(최대 5명)와 채팅하며 관계를
쌓고, 그중 한 명을 "메인 연인"으로 지정한다. 메인 연인은 전화가 가능하고 연락
빈도가 높으며, 나머지 캐릭터는 채팅만 가능하다. AI가 사용자의 취향과 일상을
기억하고 실제 연인처럼 대화를 이어가는 것이 핵심 가치다.

### 화면 흐름

```
온보딩 → 홈 → 채팅 → 마이페이지
```

- **온보딩**: 카카오 로그인 → 약관 동의 → 내 프로필 입력 → 이상형(캐릭터) 설정
  (기본정보 / 말투·관계·연애온도 / 매력 / 선호 통화 시간대) → 등록 완료
- **홈**: 메인 연인 기준 관계 통계(관계 일수·통화 횟수·연속 통화), 예약 통화,
  지난 활동 알림, 통화 기록, 메인 연인 교체
- **채팅**: 카톡형 채팅방 목록, 실시간 메시징, 사진 전송, 매니저 안내방
- **마이페이지**: 프로필/이상형 정보 수정, 캐릭터 관리, 알림 설정, 고객지원

---

## 2. 기술 스택

- Java 17 / Spring Boot 3.x
- Spring Data JPA / Spring Security
- MySQL
- Lombok
- AWS S3 (이미지 저장)
- FCM (푸시 알림)
- 실시간: 채팅은 SSE, 통화는 WebSocket

---

## 3. 패키지 구조 / 아키텍처

루트 패키지: `com.example.umcCall`

도메인형 패키지 구조를 따른다. 각 도메인은 독립적으로
controller / service / repository / entity / dto / exception 등을 가진다.

```
com.example.umcCall
├── global
│   ├── apiPayload
│   │   ├── ApiResponse
│   │   ├── PageResponse
│   │   └── code
│   │       ├── BaseSuccessCode      (인터페이스)
│   │       ├── BaseErrorCode        (인터페이스)
│   │       ├── GeneralSuccessCode   (공통 성공 코드)
│   │       └── GeneralErrorCode     (공통 에러 코드)
│   ├── exception
│   │   ├── BaseException            (비즈니스 예외 최상위)
│   │   └── GlobalExceptionAdvice
│   ├── entity
│   │   └── BaseTimeEntity
│   └── config (SecurityConfig, CorsConfig 등)
└── domain
    ├── auth
    ├── member
    ├── character
    ├── relationship
    ├── chat
    ├── call
    └── notification
        └── 각 도메인: controller / service / repository / entity / dto / exception / enums
```

- enum은 도메인 내부 `enums` 패키지로 묶어 관리한다.
- enum ↔ DB 매핑에 Converter를 둘 수 있으나 필수는 아니다(선택).

---

## 4. 공통 응답 & 에러 처리 표준

### 4-1. 응답 Envelope

모든 API 응답은 `ApiResponse<T>`로 감싼다. 형식은 다음으로 고정한다.

```json
{
  "isSuccess": true,
  "code": "COMMON200_1",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": { }
}
```

필드 순서는 `isSuccess → code → message → result`로 고정한다.
별도의 에러 객체(`ResponseEntity<ErrorResponse>` 등)를 루트로 직접 반환하지 않는다.

### 4-2. ApiResponse 사용

```java
// 성공 (기본 코드)
return ApiResponse.onSuccess(result);
return ApiResponse.onSuccess();               // result 없음

// 성공 (도메인별 성공 코드)
return ApiResponse.onSuccess(ChatSuccessCode.ROOM_LIST_OK, result);

// 실패 (전역 예외 핸들러에서 처리)
ApiResponse.onFailure(errorCode);             // 코드 기본 메시지
ApiResponse.onFailure(errorCode, message);    // 검증 메시지 등 커스텀 메시지
```

### 4-3. 코드 체계 (인터페이스 + 도메인별 구현체)

전역은 `BaseSuccessCode` / `BaseErrorCode` 인터페이스만 정의하고,
각 도메인은 이 인터페이스를 구현한 자신의 코드 enum을 만든다.
`ApiResponse`는 인터페이스에만 의존하므로 어떤 도메인 코드든 받을 수 있다.

```java
public interface BaseErrorCode {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}
```

**코드 형식**: `{도메인}{HTTP상태}_{일련번호}`

```
COMMON400_1   (공통)
CHAT404_1     (채팅 도메인, 404, 1번)
CALL409_1     (통화 도메인, 409, 1번)
```

> 공통 코드는 `GeneralErrorCode` / `GeneralSuccessCode`에 모아둔다.
> 현재 `GeneralErrorCode`에는 공통(COMMON) 외에 AUTH / MEMBER / EXTERNAL 항목도
> 함께 들어 있는데, 해당 도메인 enum이 생기면 그쪽으로 이관한다.

도메인별 에러 코드 구현 예시:

```java
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    EMPTY_MESSAGE(HttpStatus.BAD_REQUEST, "CHAT400_1", "빈 메시지는 보낼 수 없습니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT404_1", "채팅방을 찾을 수 없습니다."),
    CANNOT_LEAVE_MAIN(HttpStatus.BAD_REQUEST, "CHAT400_2", "메인 연인과의 채팅방은 나갈 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### 4-4. 예외 처리

- 비즈니스 예외는 도메인별 커스텀 예외(`XxxException`, `BaseException` 상속)로 던지고,
  `GlobalExceptionAdvice`가 이를 받아 `ApiResponse.onFailure`로 변환한다.
- 새 에러 상황이 필요하면 먼저 해당 도메인의 ErrorCode enum에 코드를 추가한다.
- `@Valid` 검증 실패(`MethodArgumentNotValidException`)와 파라미터 바인딩 실패는
  전역 핸들러에서 검증 메시지를 `message`에 담아 반환한다.
- DB 유니크 제약 위반은 409로 처리하되, 구체적 메시지가 필요하면 서비스에서
  먼저 중복 여부를 확인하고 도메인 예외로 던진다.

### 4-5. 페이지네이션

목록/페이지 응답은 `PageResponse<T>`를 사용한다. 커서 기반이 필요한 경우
(예: 채팅 메시지 조회) 커서용 응답 형태를 별도로 둔다.

---

## 5. 엔티티 규칙

- 모든 도메인 엔티티는 `BaseTimeEntity`를 상속해 생성/수정 시각을 자동 추적한다.
- 엔티티에 `createdAt`, `updatedAt`을 직접 선언하지 않는다.
- PK는 `bigint` (Long), AUTO_INCREMENT를 기본으로 한다.
- 현재 DB 스키마 생성/변경은 JPA `ddl-auto: update` 기준으로 관리한다.
  특정 테이블이나 컬럼만 별도 Flyway migration으로 추가하지 않는다.
- 단일 DB 안에서 완결되는 영구 삭제는 물리 삭제를 원칙으로 한다.
- 캐릭터처럼 외부 AI 서버의 Agent 상태와 함께 정리해야 하는 리소스는 예외적으로
  `deleted_at` 기반 논리 삭제를 사용한다. Spring DB의 논리 삭제와 AI 동기화 작업 등록은
  같은 트랜잭션에 넣고, AI 삭제는 멱등 API와 DB 기반 재시도 작업으로 최종 일관성을 맞춘다.
  AI 정리가 완료되기 전에는 원본 데이터를 물리 삭제하지 않는다.

---

## 6. 도메인 지도 & 담당 경계

각 도메인은 자신의 책임 범위 내에서 구현하며, 다른 도메인의 테이블·로직을
직접 수정하지 않는다. 도메인 간 상호작용은 명확한 호출 지점을 통해 이뤄진다.

| 도메인 | 담당 | 주요 테이블 | 책임 |
|---|----|---|---|
| 인증 | 준혁 | (auth), member | 카카오 로그인, 토큰, 로그아웃, 회원 |
| 회원 | 준혁 | member, term, member_term | 프로필, 약관 동의, 알림 설정 |
| 캐릭터 | 준혁 | character, character_trait | 온보딩, 캐릭터 생성/수정/삭제, 매력 |
| 관계 | 현경 | relationship, relationship_status, emotion_log, chat_summary | 관계 상태, 감정, 호감도, 메인 연인, AI 대화 요약 |
| 실시간/발신 | 현경 | live_status, outbound_schedule | AI 능동 발신 판단, 실시간 상태 |
| 채팅 | 석민 | chat_room, chat_message, chat_photo | 채팅방·메시지·사진, SSE |
| 통화 | 준우 | call, call_history | 통화(WebSocket), 기록 (AI 발신은 proactive 스케줄링이 트리거) |
| 알림/푸시 | 석민 | push_token | FCM 토큰, 발송 판정 |
| 알림 조회 | 준혁 | activity_notification, system_notification, notification_setting | 인앱 알림 목록, 알림 설정 |

### 도메인 간 협업 지점 (중요)
- **채팅방 생성**: 캐릭터 생성(캐릭터 도메인) 시 채팅방도 함께 생성된다.
  채팅방 생성 로직은 채팅 도메인이 제공하고, 캐릭터 도메인이 이를 호출하는 구조.
- **캐릭터-AI Agent 동기화**: 캐릭터 생성/삭제 트랜잭션에서 `character_sync_task`를 함께
  저장하고 별도 worker가 AI 서버에 UPSERT/DELETE를 요청한다. 실패 작업은 지수 backoff로
  재시도하며, DELETE API는 이미 삭제된 Agent에도 성공하도록 멱등성을 보장해야 한다.
- **AI 응답 생성**: 사용자 메시지 저장(채팅 도메인) 후 AI 로직(관계/AI 도메인)이
  응답을 생성하고, 채팅 도메인이 그 결과를 SSE로 전달한다.
- **메인 연인 판정**: 메인 여부(is_main)는 관계 도메인이 관리하며,
  다른 도메인은 이를 참조만 하고 직접 변경하지 않는다.
- **FCM 발송**: 발신(관계/발신 도메인) 시 알림/푸시 도메인이 음소거·알림설정
  (notification_setting, 회원/알림조회 도메인 소유)을 확인해 푸시 여부를 결정한다.


---

## 7. 새 기능 추가 규칙

새 API나 도메인을 추가할 때 따른다.

1. 응답은 항상 `ApiResponse<T>`로 감싼다.
2. 새 에러 상황은 해당 도메인의 ErrorCode enum(`BaseErrorCode` 구현)에 먼저 추가한다.
   코드 형식은 `{도메인}{HTTP상태}_{일련번호}`.
3. 비즈니스 예외는 도메인 커스텀 예외로 던지고 `GlobalExceptionAdvice`가 처리한다.
4. 엔티티는 `BaseTimeEntity`를 상속한다.
5. 새 엔드포인트는 인증 필요 여부(`authenticated()` / `permitAll()`)를 판단해
   `SecurityConfig`에 반영을 고려한다. CORS는 `CorsConfig` Bean을 따른다.
6. 다른 도메인의 테이블·로직을 직접 수정하지 않는다. 필요한 경우 호출 지점을 통한다.

---

## 8. 코드 컨벤션

- 브랜치: Git Flow
- 작업 단위: 기능 하나 = 이슈 하나 = PR 하나
- 코드 주석은 한국어로 작성
- 도메인 경계를 넘는 변경은 관련 담당과 협의 후 진행
