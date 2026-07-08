# Project AI Coding Rules & Guidelines

## 1. Tech Stack
- Java 17 / Spring Boot 3.x
- Spring Data JPA / Spring Security
- Lombok

## 2. Common Response Format
- 모든 API 응답은 `com.example.umcCall.global.common.ApiResponse<T>`로 감싸서 반환해야 합니다.
- **성공 시:** `ApiResponse.success(data)` 또는 `ApiResponse.success()` 사용
- **실패 시:** `ApiResponse.error(ErrorCode)` 또는 `ApiResponse.error(ErrorCode, data)` 사용
- validation 실패처럼 DTO/파라미터 검증 메시지를 그대로 내려야 하는 경우 `ApiResponse.error(ErrorCode, message)`를 사용해 `message`에 담습니다.
- 절대 `ResponseEntity<ErrorResponse>` 같은 별도 에러 객체를 루트로 직접 반환하지 마십시오.

## 3. Exception Handling
- 비즈니스 로직에서 의도적인 예외를 발생시킬 때는 반드시 `BaseException`을 사용합니다.
- 예시: `throw new BusinessException(ErrorCode.USER_NOT_FOUND);`
- 새로운 에러 상황이 필요하면 `com.example.umcCall.global.error.ErrorCode` enum에 규칙에 맞는 에러 코드를 먼저 추가하십시오.
- `@RequestBody` DTO 검증 실패(`MethodArgumentNotValidException`)와 쿼리 파라미터/ModelAttribute 바인딩 검증 실패(`BindException`)는 전역 핸들러에서 `ApiResponse.message`로 검증 메시지를 반환합니다.
- DB 유니크 제약 위반 등 `DataIntegrityViolationException`은 409 Conflict(`DUPLICATE_RESOURCE`)로 처리합니다. 도메인별로 더 구체적인 메시지가 필요하면 서비스 레이어에서 먼저 중복 여부를 확인하고 `BaseException`으로 `DUPLICATE_EMAIL`, `DUPLICATE_NICKNAME` 같은 도메인 에러를 던지십시오.

## 4. Entity Rules
- 모든 도메인 엔티티(Entity)는 생성 시각과 수정 시각을 자동으로 추적하기 위해 반드시 `BaseTimeEntity`를 상속받아야 합니다.
- 엔티티 내부 필드에 직접 `createdAt`, `updatedAt`을 선언하지 마십시오.

## 5. Security & CORS
- Spring Security가 적용되어 있으므로 새로운 API 엔드포인트를 만들 때 인증이 필요한지(`anyRequest().authenticated()`), 혹은 비인증 허용인지(`permitAll()`) 명확히 구분하여 `SecurityConfig` 반영을 고려하십시오.
- CORS 관련 설정은 `CorsConfig`에 정의된 Bean을 따릅니다.
