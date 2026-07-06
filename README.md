# 🔥 Project Convention

> `src/main` 하위에 `resources` 디렉토리와 `application.yml`를 생성해주세요!

## 🛠️ Build Info
- **Language** : Java 17
- **Framework** : Spring Boot 3.3.4
- **Database** : MySQL

---

## 📌 Git Branch Strategy
우리 프로젝트는 **Git Flow** 전략을 기반으로 하며, 모든 기능 개발은 Issue 기반으로 브랜치를 생성하여 진행합니다.

### 👥 Branch Lifecycle
| Branch | Role |
| :--- | :--- |
| `main` | - 최종 출시 및 배포용 브랜치<br>- `develop` 브랜치에서 안정성이 검증된 버전만 병합 |
| `develop` | - 다음 버전을 위한 개발 중심 브랜치<br>- 코드 리뷰 후 자유롭게 병합 |
| `feat/#이슈번호` | - 새로운 기능 개발 |
| `refactor/#이슈번호` | - 코드 리팩토링 |
| `fix/#이슈번호` | - 버그 수정 |
| `chore/#이슈번호` | - 빌드 설정, 의존성 패키지 관리, 환경 설정 등 기타 작업 |

---

## 📋 Commit Convention
커밋 메시지는 **Gitmoji + Type: 작업 내용** 형태로 작성하며, 끝맺음은 명사(~ 추가, ~ 작업, ~ 개발 등)로 통일합니다.

### 🎨 Commit Type & Gitmoji
| Gitmoji | Type | Description |
| :---: | :--- | :--- |
| 🎉 | `init` | 프로젝트 초기 세팅 |
| ✨ | `feat` | 새로운 기능 추가 |
| 🐛 | `fix` | 버그 수정 |
| ♻️ | `refactor` | 코드 리팩토링 |
| 🔥 | `del` | 파일 삭제 및 코드 제거 |
| 📝 | `docs` | 문서 수정, 목데이터 작업 등 |
| 🧪 | `test` | 테스트 코드 작성 및 수정 |
| 🛠️ | `chore` | 빌드 시스템, 패키지 매니저 관련 수정 (설정 변경 등) |
| 🪄 | `perf` | 성능 개선 |
| 🔄 | `ci` / `cd` | CI/CD 파이프라인 관련 수정 |
| ⚠️ | `revert` | 코드 되돌리기 (특정 커밋 복구) |

> **💡 커밋 메시지 예시**
> * `✨ feat: 메인페이지 개발`
> * `♻️ refactor: 등록 플로우 - 글 작성 페이지 로직 정리`
> * `🐛 fix: 로그인 토큰 만료 에러 수정`

---

## 💻 Code Convention
일관성 있는 코드 스타일과 유지보수성을 위해 다음 규칙을 준수합니다.

* **네이밍 규칙 (Naming Conventions)**
  * **Class / Interface** : UpperCamelCase (PascalCase) 사용 (`UserService`, `OrderController`)
  * **Method / Variable** : lowerCamelCase 사용 (`calculateTotalPrice()`, `userId`)
  * **Constant (상수)** : 대문자와 언더바(`_`) 조합의 SNAKE_CASE 사용 (`MAX_COUNT_LIMIT`)
  * **Package** : 모두 소문자로 작성하며 단어 구분 시 점(`.`) 사용 (`com.project.convention.domain`)
* **Lombok 사용 가이드**
  * 무분별한 `@Data` 사용을 지양하고, `@Getter`, `@RequiredArgsConstructor` 위주로 사용합니다.
  * 엔티티 객체에는 `@Setter` 대신 의미 있는 비즈니스 메서드를 정의하여 사용합니다.
* **코드 포맷터**
  * 작업실행 전 인텔리제이 내장 포맷터(`Ctrl + Alt + L` / `Cmd + Option + L`)를 생활화합니다.
  * 쓰이지 않는 Import문은 항상 정리합니다 (`Ctrl + Alt + O` / `Cmd + Option + O`).

---

## 🚀 Pull Request (PR) Rules

PR 제목:
```
Gitmoji + Type: 작업 내용
```

PR 본문:

```
## Summary
- 요약

## Related Issue
- close #이슈번호

## Describe your code
   * **작업 내용 (What I Did)** : 구현한 기능의 요약 설명 작성
   * **스크린샷/결과 (Optional)** : API 테스트 결과 첨부
   * **논의사항/질문 (To Reviewers)** : 리뷰어들이 집중해서 봐주었으면 하는 부분 기술
   
## Checklist
- [ ] 리뷰어 등록
```
Related Issue의 close #이슈번호 는 PR이 main에 Merge될 때 연결된 이슈를 자동으로 닫아주는 키워드입니다.

- **Reviewers**에는 리뷰어를 한명 이상 지정해주세요.
- **Assignees**에는 PR을 작성한 본인을 지정해주세요.

**병합 전 확인**

로컬에서 먼저 develop을 pull 받아 충돌(Conflict) 없음을 확인 후 push
CI 빌드/테스트가 모두 통과했는지 확인 후 병합

---

## 👀 Code Review Rules
상호 간의 성장과 코드 품질 향상을 위해 긍정적이고 생산적인 리뷰 문화를 지향합니다.

* **리뷰 필수 인원** : PR이 main에 병합되기 위해서는 최소 **1명 이상**의 동료 리뷰어에게 Approve를 받아야 합니다.
* **리뷰어의 태도** : 
  * "왜 이렇게 작성했나요?" 보다는 "~~한 이유로 이 방식이 더 좋을 것 같은데 어떻게 생각하시나요?"와 같이 제안형 어조를 사용합니다.
  * 좋은 코드나 기발한 로직에는 아낌없는 칭찬(리액션)을 보냅니다.
* **피드백 반영** : 리뷰 요청자는 리뷰어가 남긴 코멘트에 대해 반영 여부나 의견을 반드시 댓글로 남기고, 수정이 완료되면 알려줍니다.
