---
name: resolve-fe-backlog
description: |
  Use when working the FE team's backend-backlog.md — triggers on "백로그 해소",
  "FE 백로그 확인/처리", "backend-backlog 반영", "프론트 요청 처리". Fetches the
  living backlog doc from the AntiAgingDNA_front repo, cross-references it against
  this repo's own docs/PLANNING_OPEN_ITEMS.md (many items overlap), classifies each
  open item as already-resolved / needs-code / needs-a-decision-first, implements
  the code-needing ones under this repo's Flyway/scoring-version/error-format
  discipline, and drafts the reply to hand back to FE (this repo has no write
  access to the front repo's doc).
---

# FE 백엔드 백로그 해소 가이드

## 0. 문서 두 개, 각각 source of truth

- **FE 백로그**: `https://raw.githubusercontent.com/lion-anzaanza/AntiAgingDNA_front/main/docs/backend-backlog.md`
  FE가 관리하는 **살아있는 문서**다. 매번 새로 fetch — 이전 세션 기억이나 §6 스냅샷을 답으로 쓰지 않는다.
  해결된 항목은 FE가 직접 "해결됨" 섹션으로 내리고 날짜를 적는 형식이라, **이 저장소가 그 문서를 직접
  고칠 권한은 없다고 가정**한다 (있는지 먼저 확인하고, 없으면 §4 로 간다).
- **이 저장소의 `docs/PLANNING_OPEN_ITEMS.md`**: 백엔드가 기획팀에 올린 자체 확인 요청 문서.
  FE 백로그와 **같은 질문을 다른 각도에서 던지는 경우가 많다** (예: 로그인 식별자 이메일 vs 아이디,
  마케팅 동의 필수 여부 — FE #1/#2 ↔ 여기 E-3/E-4). 항상 두 문서를 같이 대조한다. 한쪽의 결정이
  다른 쪽 항목을 그대로 해소하기도 하고, 반대로 **두 문서가 서로 다른 답을 전제하고 있으면 그 충돌이
  최우선 처리 대상**이다.

이 스킬은 절차다. 백로그 항목 자체(우선순위·개수)는 여기 하드코딩하지 않는다 — 살아있는 문서라
그 목록은 다음 갱신 때 이미 낡는다. §6 은 참고용 스냅샷일 뿐, 정답이 아니다.

## 1. 항목 분류 — 코드부터 읽고 분류한다

FE 백로그의 우선순위 라벨(P1~P4)을 보고 지레짐작하지 말 것. 항목마다 관련 엔티티/DTO/컨트롤러/
서비스/enum 을 실제로 열어본 뒤 아래 세 갈래로 분류한다:

| 분류 | 판별 기준 | 처리 |
|---|---|---|
| **A. 이미 해결됨, 통보만 필요** | 코드가 이미 FE가 원하는 대로 동작함 | 코드 변경 없음. §4 회신에 한 줄 |
| **B. 코드 수정 필요** | 코드/스펙이 FE 요구와 다르거나, 정보 자체가 스펙에 없음 | 이 저장소에서 구현 (§3) |
| **C. 백엔드 단독 결정 불가** | 기획·디자인 결정이 선행돼야 함 (커머스, 웨어러블, 새 화면의 문항 구성 등) | 구현하지 않음. `docs/PLANNING_OPEN_ITEMS.md` 에 항목을 추가하거나 기존 항목과 연결하고, FE 회신에는 "결정 대기"로 명시 |

**함정**: 코드의 주석/Javadoc이 실제 애노테이션·로직과 어긋나 있을 수 있다. 예: 어떤 필드의
Javadoc이 "0~10 표준"이라 적어놓고 실제 검증은 `@Min(1)`인 경우 — 이런 건 Javadoc이 아니라
**애노테이션·실행되는 로직**을 기준으로 판정한다. 발견하면 그 자체가 B 항목이다(주석-코드 불일치 버그).

## 2. 처리 순서

1. FE 백로그를 최신으로 fetch. **P1 → P4** 순서로 처리한다 — P1은 FE 연동을 막는 항목이라 이걸
   먼저 풀지 않으면 나머지를 풀어도 FE가 화면을 못 붙인다.
2. 항목마다:
   a. grep/Read로 관련 코드를 찾아 실제 동작을 확인한다.
   b. `docs/PLANNING_OPEN_ITEMS.md`에 같은 주제가 있는지 확인 — 있으면 두 문서를 함께 갱신할지 판단.
   c. §1 표로 분류.
   d. B면 §3 규칙을 지켜 구현 + 테스트 추가/보강.
   e. 분류 결과(A/B/C)와 근거를 항목마다 §4 회신 초안에 한 줄씩 적립한다.
3. 전체 처리 후 `./gradlew test`. 특히 구조적 누락을 잡는 가드 테스트
   (`SchemaGenerationTest`, `DiaryReplaceCoverageTest`, `RepositoryQueryDerivationTest`) 통과를 확인한다.

## 3. 코드 수정 시 지킬 것 (이 저장소 고유 규칙)

- **스키마 변경은 Flyway로만** (`src/main/resources/db/migration/V<n>__*.sql`). 엔티티만 고치면
  `ddl-auto=validate`가 부팅을 막는다.
- **채점 파라미터(`scoring.*`)를 바꾸면 `scoring.version`을 올린다** (`ScoringProperties`).
  안 올리면 신·구 파라미터로 나온 점수가 같은 버전 아래 섞인다.
- **DTO 필드를 추가/삭제하면** `DiaryReplaceCoverageTest` 류의 필드-커버리지 테스트를 갱신한다
  (없으면 새로 만들지 여부를 판단) — "필드 복사를 빼먹는" 실수를 막는 장치다.
- **에러 포맷을 새로 만들지 않는다.** `ApiExceptionHandler`에 이미 RFC 9457 `problem+json` 규격이
  있다 (도메인 예외 → `problem()` 헬퍼 / 검증 실패 → `errors` 확장 속성 / 401·404·409 매핑 기존).
  새 도메인 예외를 추가할 땐 여기에 핸들러만 등록한다.
- **스펙 자체가 백로그의 원인인 항목이 많다** ("스펙에 정보가 없어서 못 판단"류). 코드 동작만
  고치고 끝내지 말고 `@Schema`/`OpenApiConfig`/Javadoc으로 스펙에도 반영한다 — 안 그러면 같은
  질문이 다음 스펙 대조에서 또 나온다.
- **결측(null)과 0은 다르다** — 채점에 관여하는 필드를 건드릴 땐 이 원칙(§CLAUDE.md "Missing vs
  zero")을 깨지 않았는지 확인한다.
- **`Diary`/`DiaryController`/`DiaryService`는 draft**(다른 개발자 소유 도메인의 임시 구현)라고
  CLAUDE.md에 명시돼 있다 — 이 영역을 크게 뜯어고치는 결정은 코드만으로 내리지 말고 그 사실을
  회신에 같이 적는다.

## 4. FE에게 보낼 회신 초안

이 저장소는 기본적으로 `AntiAgingDNA_front` 저장소에 쓰기 권한이 없다고 가정한다. 백로그 문서를
직접 고치지 않는다. 대신:

1. 처리한 항목을 번호 순서(P1부터)로 정리한 **회신 텍스트**를 작성한다. 항목마다: 결론 1줄 +
   (필요하면) 정확한 필드명·타입·enum 값 + 관련 커밋 해시.
2. 사용자에게 회신 초안을 보여주고 전달 방법을 확인한다:
   - front 저장소에 PR을 열어 해당 항목을 "해결됨" 섹션으로 옮기고 날짜를 적는다 (쓰기 권한이
     실제로 있을 때만, 그리고 PR을 여는 것 자체를 진행 전에 사용자에게 확인한다)
   - 아니면 회신 텍스트만 전달하고 사용자가 다른 채널(이슈·메신저 등)로 옮긴다
3. **코드만 고치고 회신을 건너뛰지 않는다.** 백로그는 FE 쪽 문서라, 백엔드가 조용히 고쳐도 FE는
   모른다 — 이 문서가 계속 쌓이는 이유 자체가 "물어봤는데 답이 안 왔다"이다.

## 5. 자가 검증

- [ ] FE 백로그를 이번에 새로 fetch했는가 (과거 세션 기억을 답으로 쓰지 않았는가)
- [ ] `docs/PLANNING_OPEN_ITEMS.md`와 교차 확인했는가, 충돌이 있으면 그것부터 처리했는가
- [ ] 각 항목을 실제 코드/애노테이션 기준으로 A/B/C 분류했는가 (Javadoc만 보고 판단하지 않았는가)
- [ ] B 항목은 Flyway·scoring.version·에러 규격·OpenAPI 스펙 규칙을 지켰는가
- [ ] `./gradlew test` 통과 (구조 가드 테스트 포함)
- [ ] FE 회신 초안을 작성했고, 전달 방법(PR vs 텍스트 전달)을 사용자에게 확인했는가

## 6. 부록 — 2026-08-16 스냅샷 (참고용, 재검증 필수)

이 스킬을 처음 만들 때 두 문서를 대조하며 발견한 것들이다. **다음에 이 스킬을 쓸 때는 반드시
다시 확인** — 그 사이 코드도 FE 백로그도 바뀌어 있을 수 있다.

| FE 항목 | 분류(당시) | 근거 |
|---|---|---|
| P1-1 `agreements` 형태 | B(부분) | `SignUpRequest.agreements: Map<AgreementType,Boolean>`, 실제 키는 `TERMS_OF_SERVICE`/`PRIVACY_SENSITIVE`/`MARKETING`/`AGE_OVER_14` — FE가 기대한 `service`/`sensitive`/`marketing`/`age`와 다르다. 마케팅은 이미 `required=false`(법적 이슈 반영 완료). `Map` 타입이라 OpenAPI에 속성이 안 드러남 → `@Schema` 보강 필요 |
| P1-2 로그인 식별자 | **충돌 — 최우선 재확인** | FE: "2026-08-16 기획 결정, 아이디 로그인". 코드: `LoginRequest.email`, `User`에 별도 id 컬럼 없음. `docs/PLANNING_OPEN_ITEMS.md` E-3은 "이메일 유지, 화면 라벨만 수정" 방향 — 이 문서보다 먼저 쓰였을 수 있다. **작성일이 같아 어느 쪽이 최신 결정인지부터 확인** |
| P1-3 인증 필요 엔드포인트 | A | `SecurityConfig`에 필터체인·공개 엔드포인트 목록 존재 — 정리해서 회신 |
| P1-4 에러 규격 | A | `ApiExceptionHandler` — RFC9457, 400(+`errors`)/401/404/409 이미 구현. 스펙 문서화만 필요 |
| P1-5 sleep 포맷 | 미확인 | `Diary` 엔티티/`DiaryRequest` 재확인 |
| P1-18 가입 아이디 입력란 | P1-2와 동일 이슈 | — |
| P1-19 검증 규칙 | B(부분) | password `@Size(8,72)`만 있고 문자 종류 제약 없음(placeholder "영문 숫자 포함"과 불일치). nickname `@Size(max=32)`만. 아이디 규칙은 P1-2 결정 후 |
| P1-22 점수→등급 | B | `DailyScoreResponse`에 grade 필드 없음 — 미구현 |
| P1-23 기록 없는 날 응답 | 미확인 | `DiaryController`/`ScoreController` 재확인 |
| P2-6 민감도 0~10 vs enum | 확인 필요 | `docs/PLANNING_OPEN_ITEMS.md` E-1: 기획은 4점 척도로 통일 확정(목업이 구버전) — `DiagnosisRequest`/`SensitivityLevel`과 대조 |
| P2-7 `stressLevel` 0 허용 | **B, 버그성** | `DiaryRequest.stressLevel` Javadoc은 "0~10 NRS 표준"인데 실제 `@Min(1)` — 주석·코드 불일치. `@Min(0)`으로 고치는 쪽이 자체 문서와도 일치 |
| P2-8 `exerciseType` 4번째 값 | A | 실제 값은 `STRENGTH_AND_AEROBIC`(근력+유산소) — "기타" 아님. 디자인 문구를 근력+유산소로 맞추라고 회신 |
| P2-9 `walkDuration` 라벨 | A | `THIRTY_TO_60` enum 주석이 이미 "30분~1시간" — FE 제안과 이미 일치, 통보만 |
| P2-20 `birthYear` 상한 | A(일부) | `@Min(1900)`뿐이라 스펙엔 상한이 안 보이지만, `AuthService.verifyAge()`가 연 나이로 만 14세 미만·미래연도를 400(`SignUpNotAllowedException`)으로 막는다 — 실제로는 검증됨, 스펙에만 안 드러남. `@Max`는 매년 값이 바뀌어 정적 애노테이션으로 못 씀 → description 명시 또는 커스텀 검증기 검토 |
| P2-21 가입 직후 로그인 상태 | A | `AuthService.signUp`이 계정+진단+약관을 한 트랜잭션에 저장 후 리턴, 컨트롤러가 즉시 토큰 발급 — 설계 확정대로 동작. 통보만 |
| P3-10~15, 24 | 대부분 C | 홈 지표 카드, 주간 추이·코멘트 문장 생성, 날씨 자동기록, 개선책(커머스 포함), 마이페이지 — 신규 도메인. 기획·우선순위 논의 선행 |
| P4-16, 17 | B(쉬움) | OpenAPI 메타데이터 채우기, 시드 계정 — 급하지 않지만 언제든 처리 가능 |
