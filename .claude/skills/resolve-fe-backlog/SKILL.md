---
name: resolve-fe-backlog
description: |
  Works the FE team's backend-backlog.md (numbered requests, 🔴 = backend's turn).
  Use when the user hands over or mentions the backlog — "백로그 해소", "FE 백로그
  확인/처리", "backend-backlog 반영", "프론트 요청 처리". Reads the backlog, checks
  each open item against the real code and the live server, cross-references
  docs/PLANNING_OPEN_ITEMS.md, classifies it (already done / implement now / needs
  기획), implements under this repo's Flyway · scoring.version · problem+json ·
  OpenAPI rules, verifies the item's 완료 조건 on production after deploy, and
  drafts one-line verbal replies in the backlog's own format.
---

# FE 백엔드 백로그 해소

## 문서 구조 — 먼저 알아둘 것

- **FE 백로그** (`backend-backlog.md`)는 FE가 소유한다. 사용자가 이 저장소 `docs/`에 사본을 넘겨주는
  것이 기본이다. 없거나 "최종 갱신" 날짜가 의심스러우면
  `https://raw.githubusercontent.com/lion-anzaanza/AntiAgingDNA_front/main/docs/backend-backlog.md`
  를 fetch한다. **백엔드는 이 문서를 고치지 않는다** — 회신은 구두(사용자가 전달)다.
- 형식: 번호는 불변. "열린 요청" 표에서 🔴 = 백엔드 차례, ⏸ = 합의된 보류(건드리지 않음).
  항목마다 **요청 / 완료 조건 / 배경 / 범위 밖 / 프론트 우회 / 회신**이 있다.
  - "회신" 접두어: `구두:` 백엔드가 한 말 · `전달:` FE가 한 말 · `확인:` FE가 서버를 직접 호출한 결과
  - FE는 **`확인:` 없이는 닫지 않는다.** 판단 근거는 운영 서버의 `/v3/api-docs`와 실제 응답뿐이다.
  - "닫힘" 표의 결정은 FE가 이미 사실로 받아들인 것이다 — 새 결정의 근거로 인용할 수 있다.
- **`docs/PLANNING_OPEN_ITEMS.md`**: 백엔드가 기획에 올린 질문. FE 항목과 같은 주제가 많다.
  두 문서가 서로 다른 답을 전제하면 그 충돌부터 처리한다.

## 진행 체크리스트 (복사해서 쓴다)

```
- [ ] 1. 백로그 읽기 — 🔴 항목만 추림, ⏸ 는 건너뜀
- [ ] 2. 항목마다 실제 상태 확인 (코드 + git + 운영 스펙)
- [ ] 3. A/B/C 분류
- [ ] 4. B 구현 + 테스트 + OpenAPI 반영
- [ ] 5. gradlew test (Docker 켜고 — 꺼져 있으면 통합 테스트 skip 됨을 보고)
- [ ] 6. PR → 머지 → 배포 후 운영에서 완료 조건 확인
- [ ] 7. 구두 회신 초안
```

## 2. 실제 상태 확인 — "구두로 고쳤다" ≠ "운영에 있다"

항목마다 세 곳을 본다:

1. **코드**: 관련 DTO·컨트롤러·서비스를 연다. Javadoc이 아니라 애노테이션·실행 로직이 기준이다
   (주석-코드 불일치 자체가 B 항목이다).
2. **git**: 회신에 "수정 완료"가 있으면 그 커밋이 `origin/main`에 있는지 (`git branch -a --contains`).
3. **운영 스펙**: `curl -s https://antiaging-dna.anzaanza.cloud/v3/api-docs` 로 필드·설명이 실제로 있는지.
   FE는 이것만 본다 — **동작은 고쳤는데 `@Operation`/`@Schema` 설명이 그대로면 FE 입장에선 안 고친 것**이다
   (#31: 수정은 배포됐는데 설명이 안 바뀌어 한 달 넘게 "배포 대기"로 남았다).

## 3. 분류 — 근거가 있으면 결정하고, 없으면 지어내지 않는다

| 분류 | 기준 | 처리 |
|---|---|---|
| **A. 이미 됨** | 운영이 이미 요구대로 동작 | 스펙 설명에 안 드러나면 설명만 보강. 회신 |
| **B. 지금 구현** | 근거가 있다: 닫힘 표의 결정, `PLANNING_OPEN_ITEMS.md`의 ✅ 결정, 화면 placeholder, RFC·업계 관례, 이 코드베이스 기존 패턴 | 결정하고 구현까지 끝낸 뒤 **통보**. FE·기획에 되묻지 않는다 |
| **C. 결정 불가** | 숫자·규칙을 지어내야만 한다 (특히 점수에 영향) 또는 기획 산출물이 선행돼야 함 | 구현 안 함. `PLANNING_OPEN_ITEMS.md`에 올리거나 연결, 회신에 "기획 대기" |

C로 보내기 전에 한 번 더 묻는다: **닫힘 표나 기존 결정을 조합하면 답이 나오지 않는가?**
예: #10 스트레스 등급 — "등급 경계 70/40은 항목 공통"(#22) + "스트레스 점수식"(#7)을 조합하면
새 숫자 없이 `stressGrade`가 나온다 → B.

## 4. 구현 규칙 (이 저장소 고유)

- 스키마 변경은 Flyway `V<n>__*.sql`로만 (`ddl-auto=validate`).
- 채점식에 들어가는 `scoring.*`를 바꾸면 `scoring.version` 상향. 등급(`scoring.grade.*`)처럼 응답 시점에만
  계산하는 표시값은 상향 불필요.
- 에러는 `ApiExceptionHandler`의 problem+json만 쓴다 — 새 포맷 금지.
- **응답 필드나 동작이 바뀌면 `@Operation` description·`@Schema`도 같이 바꾼다** (§2-3).
- 결측(null) ≠ 0. 새 필드도 미입력이면 점수·등급 모두 null.
- 방향이 헷갈릴 수 있는 값(예: 웰빙 방향 점수 vs 원시값)은 설명과 회신에 방향을 명시한다.
- Diary 도메인은 draft(다른 개발자 소유) — 크게 바꿀 땐 회신에 그 사실을 적는다.

## 6. 배포 후 완료 조건 확인

FE 항목의 "완료 조건"을 그대로 운영에서 재현한다. 테스트 계정 `demo` / `Demo1234` (#17).
스펙 확인은 `/v3/api-docs`, 동작 확인은 로그인 후 해당 엔드포인트 호출.
**직접 확인하지 못했으면 회신에 "배포 완료"라고 쓰지 않는다** (2026-08-17 "배포 완료" 4건이 실제로는
운영에 없었다). 도구 권한 등으로 호출이 막히면 사용자가 돌릴 명령을 넘긴다.

## 7. 구두 회신 초안

FE가 "회신" 목록에 그대로 옮길 수 있게 **항목당 한 줄 = 한 사실**로 쓴다. 문장 설명이 길어지면
그건 FE `backend-api.md`에 들어갈 내용이다 — 필드명·타입·enum·방향만 적는다.

```
#10 구두: /api/scores/items에 stressLevel·stressScore·stressGrade 추가 · 배포됨
#10 구두: stressScore는 웰빙 방향(높을수록 좋음) → 스트레스 7~10은 DANGER · 경계는 22번과 동일
#31 구두: 수정본 운영 배포됨 · /api/scores/{date} 설명에 "조회만으로 행 생성 안 함" 반영
```

B 항목은 "이렇게 했습니다"로 쓴다 — "이렇게 해도 될까요?"가 아니다. 질문은 C 항목에만 한다.
