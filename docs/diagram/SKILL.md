---
name: antiagingdna-erd
description: |
  Use when generating or updating the ERD diagram (drawio) for the
  AntiAgingDNA_API 백엔드 저장소 — 트리거: "ERD 그려줘", "ERD 업데이트", "스키마 ERD",
  "ERD.drawio 갱신", "엔티티 관계도 그려", "테이블 추가해서 ERD 반영". 대상 파일은
  `docs/diagram/ERD.drawio` (mxGraphModel) 하나뿐이다. 이 프로젝트는 Spring Boot
  (JPA/MySQL) 이고 Prisma 같은 별도 스키마 파일이 없으므로, `src/main/java/**/*.java` 에
  `@Entity` 클래스가 있으면 그것이 source of truth 이고 ERD 는 거기서 파생시킨다. 아직
  엔티티 코드가 없다면 ERD 자체와 이 문서 §0 체크리스트가 잠정 source of truth 다.
  엔티티는 절대 겹치지 않게, 관계선 머리는 절대 포개지지 않게, 좌표는 산식으로 계산한다.
  drawio 좌표/스타일 규약은 [[drawio-ia]] 스킬과 동일 격자 원칙을 따른다.
---

# ERD (drawio) 작성 가이드라인 — AntiAgingDNA_API

`docs/diagram/ERD.drawio` (mxGraphModel) 하나를 그리거나 갱신한다. (경로 주의: `docs/diagram/`
**단수**다 — 다른 프로젝트의 `docs/diagrams/` 복수형과 혼동하지 말 것.)

**source of truth 판단 순서**:

1. `src/main/java` 아래 `@Entity` 클래스가 있는지 먼저 확인한다 (`grep -rl "@Entity" src/main/java`).
   있다면 그 코드가 source of truth — ERD 는 거기서 기계적으로 파생되는 산출물이다.
2. 아직 없다면 (2026-08 기준 이 저장소의 실제 상태 — 코드는 `AntiagingdnaApplication`/
   `CorsConfig`/`HealthController` 뿐이고 엔티티는 없다) **ERD 자체가 설계 산출물**이다.
   이 경우 §0 의 "현재 테이블/관계 체크리스트" 를 함께 최신 상태로 유지해 사실상의 소스로 삼는다.
   엔티티 코드가 생기는 순간부터는 1번으로 전환한다.

> 이 문서는 v1.0 — 다른 프로젝트(Prisma 스키마 기반)의 `prisma-erd` 스킬을 이 저장소의
> 실제 스택(Spring Boot/JPA/MySQL)과 실제 ERD 내용(사용자·DNA 정보·일지)에 맞게 재작성한 것.
> 실제로 그려본 뒤 발견되는 예외·관용은 §13 에 계속 보강한다.

> **작성 방식**: 현재 3테이블·2관계 규모라 손으로 Read→Edit 해도 충분히 안전하다. 다만
> 규모가 커지면(예: 10개+ 테이블) 손으로 좌표/높이/앵커를 찍다가 실수가 급증하므로, 그 시점엔
> tables/edges 배열 → 높이 자동 계산 → XML 직렬화 → 겹침/앵커/관통 자가검증까지 한 번에
> 하는 생성 스크립트(예: `scripts/gen-erd.mjs`) 도입을 고려한다. **이 저장소엔 아직 그런
> 스크립트가 없다** — 만들 경우 이 문서의 §2~§4 규칙을 그대로 인코딩할 것.

> **[[drawio-ia]] 와의 관계**: 좌표를 눈대중이 아닌 산식으로 계산하는 것, 충돌 검증
> 4-부등식 패턴, 명시적 exit/entry, lane 분리라는 **기본 원리는 [[drawio-ia]] §1·§2·§4
> 를 그대로 가져온 것**이다. 이 문서 §2/§4 는 그 원리를 ERD 의 폭-400 고정 테이블 도형에
> 맞춰 상수 이름/값만 바꿔 재적용했을 뿐이다 — `TABLE_W/H_GUTTER/V_GUTTER/LANE_W` 는
> drawio-ia 의 `COL_W/ROW_H/GUTTER` 에 대응한다. **자동으로 동기화되지 않으므로** drawio-ia
> 의 공식/상수를 고치면 이 문서의 대응 값도 함께 검토할 것. drawio-ia 에 없는 진짜
> ERD 전용 내용은 §2.1(3) 의 collinear 겹침 검사, §4.3 의 다중 엣지 분수 분산 규칙,
> 그리고 §3/§5~§9(테이블 도형, 타입/제약/카디널리티 매핑, 한글 라벨, 레이아웃) 뿐이다.

---

## 0. 가장 먼저 — drift 점검

1. `@Entity` 클래스가 있으면: 모든 엔티티의 필드/관계를 ERD 와 1:1 대조해 drift 목록을 먼저
   텍스트로 뽑은 뒤 그린다.
2. 없으면: 아래 체크리스트를 최신 요구사항(사용자 요청, 기획 문서)과 대조한다. **이 표를
   갱신하지 않은 채 ERD 만 고치는 것 금지** — "ERD 에 있으니 맞겠지" 는 항상 금지.

### 현재 테이블 체크리스트 (ERD 실측 기준)

| 테이블 (헤더 표기) | 데이터 행 수 | 비고 |
|---|---|---|
| 사용자(user) | 3 | id(PK, VARCHAR(64)), login_id(NN, VARCHAR(64)), password(NN, VARCHAR(255)) |
| DNA 정보(dna_info) | 15 | id(PK, VARCHAR(64)) + 14개 컬럼, 전부 타입 기입 완료. `sleep_type`/`smoking_frequency`/`drink_frequency`/`life_rhythm`/`weekend_rhythm` 은 정수 enum 코드(`INT`, `@Enumerated(EnumType.ORDINAL)` 전제)로 확정 |
| 일지(diary) | 6 | id(PK, VARCHAR(64)), author_id(FK, NN, VARCHAR(64)) + 4개 컬럼, 전부 타입 기입 완료 |

### 현재 관계 체크리스트

| 부모 | 자식 | 카디널리티 | 비고 |
|---|---|---|---|
| user | dna_info | 1 : 1 | dna_info.id 가 user.id 와 PK 를 공유(별도 FK 컬럼 없음). 제약 칸은 `PK, FK` 로 표기(§6) |
| user | diary | 1 : 0..N | diary.author_id (FK, NN) |

**해결된 부채 (2026-08-07)**: TYPE 플레이스홀더 전부 채움(§5 기준), `weekly_exercise_ frequency`
공백 오타 수정(`weekly_exercise_frequency`), 두 관계 엣지 모두 `exitX/exitY/entryX/entryY` 명시
완료 — 기존 waypoint 좌표와 정확히 맞도록 역산했다 (user 하단 0.5→dna_info 상단 0.5 직선,
user 하단 0.7→diary 상단 0.5, waypoint (360,240)/(760,240) 그대로 유지). `dna_info.id` 제약
칸도 PK 공유형 1:1 관례에 맞춰 `PK, FK` 로 보완. 컬럼명 네이밍은 `is_smoker`/`is_shift_worker`/
`is_drinker` 로 snake_case 통일(사용자 확인 완료). `sleep_type`/`smoking_frequency`/
`drink_frequency`/`life_rhythm`/`weekend_rhythm` 은 카테고리 문자열(`VARCHAR(32)`) 대신 정수
enum 코드(`INT`)로 확정(사용자 확인 완료) — 실제 엔티티에서는 Java enum + `@Enumerated` 로
관리. `VARCHAR(n)` 길이값(login_id 64, password 255 등)도 그대로 확정.

**남은 부채 (지금 임의로 고치지 말 것 — 사용자가 요청할 때 처리)**:

- 없음. 새로 발견되는 항목은 이 자리에 추가할 것.

---

## 1. 출력 포맷 규약

- 단일 페이지 `<mxfile>` → `<diagram>` → `<mxGraphModel>` → `<root>`.
- 좌표는 정수, **10 의 배수** (`gridSize="10"`). **눈으로 찍지 말고 산식으로 계산** (§2).
- 한글 라벨 그대로. `&` `<` `>` 만 escape, 줄바꿈은 `&#xa;`.
- 기존 파일 수정 시 **반드시 Read 후 Edit** — drawio 가 attribute 순서를 reformat 했을 수 있음.
- 테이블 1개 = `shape=table` 1개 (헤더 + 데이터 행마다 1 row). 테이블 폭 고정 **400**.

---

## 2. 겹침 금지 & 간격 — ERD 테이블 도형에 대한 구체 규칙

기본 원리(산식으로 좌표 확정, 그리기 전 충돌 검증)는 서두의 "[[drawio-ia]] 와의 관계" 참고.
아래는 그 원리를 ERD 테이블 도형(폭 400, 가변 높이)에 적용할 때의 구체 값/규칙이다.

### 2.1 불변 규칙

1. **엔티티(테이블)는 절대 겹치지 않는다.** (hard fail — 1px 라도 겹치면 실패)
2. **관계선 머리(crow's-foot / ERone 막대)는 절대 포개지지 않는다.** 한 테이블 경계의
   한 점에 두 엣지가 붙으면 안 된다.
3. **(drawio-ia 에 없는 ERD 전용 보강) 서로 다른 두 엣지의 선분이 같은 직선 위에
   포개지면(collinear overlap) 안 된다.** 같은 lane(`x` 또는 `y`)을 두 엣지가 같은 구간에서
   공유하면 한 줄처럼 보여 관계를 오독한다. → lane(waypoint 의 `x`/`y`)은 **엣지마다 다르게**
   배정. 좁은 band gap 에 여러 엣지를 몰 때는 lane 을 20~30px 씩 벌려 분산 (§4.5). 앵커(머리)
   중복 검사는 테이블 경계점만 보므로 **중간 lane 공유는 못 잡는다** — collinear 검사는
   반드시 별도로 한다.
4. 관계선 몸통이 **점에서 교차**(가로지름)하는 것은 불가피하면 허용. **포개짐(3)은 불허.**

### 2.2 간격 상수 (폭 400 테이블 기준 — IA 노드 모듈보다 크게 잡는다)

```
TABLE_W   = 400                    (고정)
HEADER_H  = 30,  ROW_H = 30
table.height = 30 + 30 × (데이터 행 수)   ← 반드시 행 수로 계산. 절대 고정값 쓰지 말 것
H_GUTTER  = 240 (최소) / 320 (권장)    ← 같은 밴드 좌우 테이블 사이 빈 가로 공간
V_GUTTER  = 160 (최소) / 220 (권장)    ← 위 테이블 실제 bottom ~ 아래 테이블 top
LANE_W    = 80                       ← 테이블 사이를 지나는 엣지 1개당 확보 폭
```

→ 같은 밴드 인접 테이블 **중심 간 거리 ≥ TABLE_W + H_GUTTER = 640**.
→ 테이블 사이로 엣지가 k 개 지나가면 그 틈을 `H_GUTTER + k×LANE_W` 로 넓힌다.

### 2.3 밴드 y 는 고정값이 아니라 계산값

세로 겹침의 주범: 필드가 많은 테이블(예: 지금의 `dna_info`, 높이 480)과 적은 테이블(`사용자`,
높이 120)을 같은 고정 y 에 둠. **각 테이블 높이를 먼저 계산**한 뒤, 다음 밴드 y 를 위 밴드
실제 바닥에서 산출한다:

```
band[k].y = max( band[k-1] 모든 테이블의 (y + height) ) + V_GUTTER + (구분선/라벨 40, 없으면 생략)
```

### 2.4 충돌 검증 — XML 쓰기 전 좌표표로 전수 검사

공식 형태는 [[drawio-ia]] §2 와 동일(같은 4-부등식 OR 패턴) — `A.right`/`A.bottom` 대신
이 문서의 `x+w`/`y+h` 표기를 쓰고, gutter 값만 §2.2 의 ERD 상수로 바꿔 대입한다.

도형 좌표표(id, x, y, w, h)를 텍스트로 먼저 작성하고, **모든 테이블 쌍 (A,B)** 에 대해
아래 4식 중 **하나라도** 성립하는지 확인:

```
A.x + A.w + H_GUTTER ≤ B.x      (A 가 B 왼쪽, 충분히 떨어짐)   OR
B.x + B.w + H_GUTTER ≤ A.x      (B 가 A 왼쪽)                 OR
A.y + A.h + V_GUTTER ≤ B.y      (A 가 B 위)                   OR
B.y + B.h + V_GUTTER ≤ A.y      (B 가 A 위)
```

한 쌍이라도 4식 모두 실패 = 겹침/근접 → 좌표 재배치 후 **다시 전수 검사**. 통과해야 XML 시작.
(이 표가 [[drawio-ia]] §2 의 격자 충돌표와 같은 역할. 테이블은 가변 높이라 `h` 계산이 핵심.)

---

## 3. drawio 빌딩 블록 (이 ERD 의 실제 고정 스타일 — `docs/diagram/ERD.drawio` 에서 그대로 추출)

### 3.1 엔티티 헤더 (`shape=table`)

```
value="한글명(영문 테이블명)"                예) "사용자(user)", "DNA 정보(dna_info)", "일지(diary)"
style="shape=table;startSize=30;container=1;collapsible=1;childLayout=tableLayout;fixedRows=1;rowLines=1;fontStyle=1;align=center;resizeLast=1;html=1;rounded=1;arcSize=14;absoluteArcSize=1;fillColor=light-dark(#FFCC99,#663300);horizontal=1;swimlaneFillColor=default;fontSize=12;"
geometry: width=400, height = 30 + 30 × (데이터 행 수)
```

### 3.2 행 (`shape=tableRow`) — 부모 = 테이블 id

```
style="shape=tableRow;horizontal=0;startSize=0;swimlaneHead=0;swimlaneBody=0;fillColor=none;collapsible=0;dropTarget=0;points=[[0,0.5],[1,0.5]];portConstraint=eastwest;rounded=1;arcSize=14;absoluteArcSize=1;fontSize=12;fontStyle=1;"
geometry: y = 30 + 30×rowIndex, width=400, height=30   (rowIndex 0부터)
```

### 3.3 셀 4개 (`shape=partialRectangle`) — 부모 = 행 id

| 셀 | 내용 | x | width | align |
|---|---|---|---|---|
| 1 | 한글 컬럼명 | 0 | 100 | `align=center` |
| 2 | DB 컬럼명 | 100 | 160 | `align=left;spacingLeft=6` |
| 3 | 타입 | 260 | 90 | `align=center` |
| 4 | 제약 (PK/FK/NN/UQ) | 350 | 50 | `align=center` |

셀 공통 스타일: `shape=partialRectangle;connectable=0;fillColor=none;overflow=hidden;whiteSpace=wrap;html=1;rounded=1;arcSize=14;absoluteArcSize=1;<align>;fontSize=12;fontStyle=1;`
각 셀은 내부에 `<mxRectangle width=<w> height=30 as="alternateBounds"/>` 동봉.

---

## 4. 엣지 라우팅 & 머리 분산 — ERD 관계선에 대한 구체 규칙

"모든 엣지에 exit/entry 4속성을 명시하고 자동 라우팅에 맡기지 않는다"는 원칙 자체는
[[drawio-ia]] §4 와 동일하다. ERD 에서 달라지는 점은 crow's-foot 화살표(§4.1)와, 한 테이블의
한 변에 관계가 여러 개 몰릴 수 있어 고정 0.5 대신 **분수를 등분해 분산**해야 한다는 것(§4.3,
drawio-ia 에는 없는 확장 — IA 노드는 변 하나에 엣지가 몰리는 경우가 드물다).

### 4.1 엣지 스타일 (crow's-foot, 이 ERD 실사용 스타일)

```
공통     edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;
1:N      + endArrow=ERzeroToMany;endFill=0;startArrow=ERone;startFill=0;
0..1:N   + endArrow=ERzeroToMany;endFill=0;startArrow=ERzeroToOne;startFill=0;
1:1      + endArrow=ERone;endFill=0;startArrow=ERone;startFill=0;
```

- **source = 부모(참조되는 쪽, PK 보유)** → `startArrow` 가 부모 머리.
- **target = 자식(FK 보유)** → `endArrow` 가 자식 머리.
- 머리는 **양 끝 모두**에 존재하므로 분산은 source·target 양쪽에 적용.

### 4.2 변 선택 + exit/entry 명시 (자동 라우팅 금지)

기본 방향별 매핑은 [[drawio-ia]] §4 와 같은 원리다 — 다만 IA 는 방향당 고정 `0.5` 를 쓰고,
ERD 는 한 변에 여러 관계가 몰릴 수 있어 아래처럼 변수 분수 `f`/`g` 로 일반화한다.
두 테이블의 상대 위치로 어느 변에 붙일지 정하고, 4 속성을 항상 명시한다:

| 자식이 부모의 | 부모 exit | 자식 entry |
|---|---|---|
| 오른쪽 | `exitX=1; exitY=f` | `entryX=0; entryY=f` |
| 왼쪽 | `exitX=0; exitY=f` | `entryX=1; entryY=f` |
| 아래 | `exitX=g; exitY=1` | `entryX=g; entryY=0` |
| 위 | `exitX=g; exitY=0` | `entryX=g; entryY=1` |

`f`, `g` 는 그 변에 붙는 엣지들에 **서로 다르게** 배분하는 분수(0~1).

### 4.3 머리 분산 규칙 (가장 중요)

**한 테이블 경계의 (변, 분수) 조합은 그 테이블에 닿는 모든 엣지에서 전부 달라야 한다.**
한 변에 엣지가 m 개면 분수를 등분: m=2 → `0.33, 0.66`, m=3 → `0.25, 0.5, 0.75`, m=4 → `0.2,0.4,0.6,0.8`.
**같은 (변,분수) 두 번 사용 = 머리 겹침 = 실패.**

지금은 `user` 에 관계가 2개(dna_info, diary) 뿐이라 변을 나눠 붙이면(예: 아래로 dna_info,
오른쪽으로 diary) 분산 문제가 거의 없다. 테이블/관계가 늘어나 한 테이블에 관계가 몰리면
(허브 테이블) §4.4 를 적용한다.

### 4.4 허브 테이블 (엣지 다수로 몰릴 경우 대비)

향후 `user` 나 다른 테이블에 관계가 4개 이상 몰리면:
- 허브를 레이아웃 **중앙**에 두고, 엣지를 **여러 변에 나눠** 내보낸다.
- 각 변 안에서 §4.3 으로 분수 분산.

### 4.5 waypoint (테이블 가로지름 방지)

- A→C 엣지가 B 를 가로지르면 `<Array as="points">` 로 우회. 좌표는 **격자(10배수)·테이블 사이 빈 lane**.
- 평행하게 달리는 엣지 여러 개는 서로 다른 lane (`x` 또는 `y` 를 LANE_W 간격으로) 으로 분리.
- waypoint 없이 멀리 떨어진 두 테이블을 직선 연결하면 중간 테이블을 통과하기 쉽다 — 반드시 확인.

---

## 5. Java/JPA 타입 → 표시 타입 매핑 (MySQL 기준)

이 프로젝트 DB 는 **MySQL** 이다(`spring.jpa.properties.hibernate.dialect=MySQLDialect`,
`mysql-connector-j`) — Postgres 전용 타입(`TIMESTAMPTZ`, `BIGSERIAL` 등)은 쓰지 않는다.

| Java/JPA 타입 | ERD 타입 셀 |
|---|---|
| `String` (PK, 이 프로젝트 관례) | `VARCHAR(64)` |
| `String` (`@Column(length=n)`) | `VARCHAR(n)` |
| `String` (`@Lob`/대용량 텍스트) | `TEXT` |
| `Integer`/`int` | `INT` |
| Java enum (`@Enumerated(EnumType.ORDINAL)`) | `INT` — 이 프로젝트는 카테고리성 컬럼(빈도/리듬 등)을 문자열 대신 정수 enum 코드로 저장한다(§0 확정 사항). `dna_info.sleep_type`/`smoking_frequency`/`drink_frequency`/`life_rhythm`/`weekend_rhythm` 이 실사용 예 |
| `Long`/`long` (FK 등으로 필요할 경우) | `BIGINT` |
| `Boolean`/`boolean` | `BOOLEAN` |
| `java.time.LocalDate` | `DATE` |
| `java.time.LocalTime` | `TIME` |
| `java.time.LocalDateTime` | `DATETIME` |
| `java.math.BigDecimal` (`precision`,`scale`) | `DECIMAL(p,s)` |

**이 프로젝트 관례**: PK 컬럼은 auto-increment 정수가 아니라 **`VARCHAR(64)` 문자열 ID** 를
쓴다 (`사용자`/`dna_info`/`일지` 모두 `id VARCHAR(64) PK`). 새 테이블을 추가할 때도 이 관례를
따른다 — 별도 지시가 없는 한 `BIGINT AUTO_INCREMENT` 로 바꾸지 말 것.

아직 엔티티 코드가 없는 컬럼(§0 의 TYPE 미기입 목록)은 컬럼명과 도메인 지식으로 타입을
추정해 채우되, 확신이 서지 않는 정밀도/길이(`VARCHAR(n)`, `DECIMAL(p,s)`)는 사용자에게 확인한다.

---

## 6. 제약(4번 셀) 매핑

| JPA / 관례 | 제약 표기 |
|---|---|
| `@Id` (단일 PK) | `PK` |
| 복합키(`@EmbeddedId`/`@IdClass`) | 해당 컬럼 모두 `PK` |
| `@Column(unique = true)` | `UQ` |
| `@ManyToOne`/`@OneToOne` 의 FK 컬럼(`@JoinColumn`) | `FK` |
| `@Column(nullable = false)` 또는 primitive 타입 | `NN` |
| optional(Wrapper 타입, `nullable = true`) | (제약 칸 비움 — NULL 허용) |
| PK 를 공유하는 1:1(`@MapsId`) | `PK, FK` 병기 — `dna_info.id` 가 실사용 예 |

조합 표기: `FK, NN`, `UQ, NN`, `PK, FK` 등. 콤마+공백으로 나열 (기존 ERD 관용, `diary.author_id`
가 `FK, NN` 실사용 예).

---

## 7. relation → crow's-foot 카디널리티

JPA 관계 양쪽을 보고 결정한다. **`@***ToMany`/컬렉션 쪽이 N, 단일 참조 쪽이 1**.

| 패턴 (부모 / 자식) | source(부모) 화살표 | target(자식) 화살표 | 의미 |
|---|---|---|---|
| 부모 `@OneToMany(mappedBy=...)` / 자식 `@ManyToOne` NOT NULL FK | `ERone` | `ERzeroToMany` | 1 : 0..N |
| 부모 `@OneToMany(mappedBy=...)` / 자식 `@ManyToOne` optional FK | `ERzeroToOne` | `ERzeroToMany` | 0..1 : 0..N |
| `@OneToOne` + PK 공유(`@MapsId`) 또는 FK `unique=true` | `ERone` | `ERone` | 1 : 1 |

화살표 종류: `ERone`(¦ 정확히 1), `ERzeroToOne`(○¦ 0 또는 1), `ERzeroToMany`(○< 0 이상),
`ERoneToMany`(¦< 1 이상), `ERmany`(< 다수).

관계 전수 목록은 §0 의 "현재 관계 체크리스트" 를 그대로 따른다 — 새 관계를 추가/변경하면
그 표부터 갱신한 뒤 엣지에 반영한다(중복 관리 금지, §0 표가 유일한 목록).

---

## 8. 한글 라벨 컨벤션

- **테이블 헤더 형식**: `한글명(영문 테이블명)` — 괄호 앞 공백 없음, 영문은 실제 DB 테이블명
  (snake_case). 예) `사용자(user)`, `DNA 정보(dna_info)`, `일지(diary)`.
  (다른 프로젝트의 `테이블명(@@map값) (한글명)` 형식과 순서가 반대이니 혼동 주의.)
- **컬럼 한글명 용어집** (현재 ERD 에서 추출, 일관 유지 — 새 컬럼 추가 시 이 표에도 추가):

| DB 컬럼 | 한글 | DB 컬럼 | 한글 |
|---|---|---|---|
| id | 아이디 | sleep_quality | 수면 품질 |
| login_id | 로그인아이디 | caffein_sensitivity | 카페인 민감도 |
| password | 비밀번호 | insulin_sensitivity | 당분 민감도 |
| author_id | 작성자 아이디 | stress_sensitivity | 스트레스 민감도 |
| total_score | 종합 점수 | weekly_exercise_frequency | 주평균 운동 수 |
| sleep_type | 수면 유형 | is_smoker | 흡연 여부 |
| smoking_frequency | 흡연 빈도 | is_shift_worker | 교대 근무 여부 |
| is_drinker | 음주 여부 | drink_frequency | 음주 빈도 |
| life_rhythm | 생활 리듬 | weekend_rhythm | 주말 리듬 |
| sleep_started_time | 취침 시간 | sleep_ended_time | 기상 시간 |
| sleep_waiting_time | 취침까지 소요 시간 | sleep_satisfaction | 수면 만족도 |

도메인 한글명: User=사용자, DnaInfo=DNA 정보, Diary=일지.

---

## 9. 레이아웃

현재 3테이블·2관계 규모에서는 단순 배치로 충분하다 (실제 ERD): `user` 를 좌상단에 두고,
1:1 관계인 `dna_info` 를 바로 아래(세로), 1:N 관계인 `diary` 를 오른쪽(가로, waypoint 로
`dna_info` 를 우회)에 배치했다.

테이블/관계가 늘어나 참조 깊이가 3단계 이상이 되면 (다른 프로젝트 ERD 에서 검증된 방식)
**참조 깊이별 가로 띠(depth band)** 레이아웃으로 전환을 고려한다:

```
Depth 1 (root)   : user
─────────── 가로 점선 구분선 (endArrow=none;dashed=1;strokeColor=#666666) ───────────
Depth 2          : dna_info, diary
─────────────────────────────────────────────────────
Depth 3          : (향후 diary/dna_info 에서 파생되는 테이블 등)
```

- 같은 depth 테이블은 **같은 y**, 좌우 간격은 §2.2 (`H_GUTTER`) 준수.
- 밴드 y 는 §2.3 산식으로 계산 (위 밴드 최대 높이 + `V_GUTTER`).
- 허브 테이블은 엣지가 사방으로 나가므로 밴드 중앙에 배치 (§4.4).
- 충돌·중심 정렬·waypoint 격자 규칙은 §2 / §4 와 [[drawio-ia]] §2~§5 를 따른다.
- **범례(legend)**: 우측에 예시 테이블(`엔티티(Entity)`, 속성명1/속성명2 예시 행) + crow's-foot
  카디널리티 마커 예시 4개 + 제약(NN/PK/FK/UQ) 텍스트 설명 블록이 있다. 갱신 시 지우지 말 것.

---

## 10. 작성 절차 (체크리스트)

1. `@Entity` 클래스 존재 여부 확인 (`grep -rl "@Entity" src/main/java`). 없으면 §0 체크리스트와
   최신 요구사항을 확인.
2. **drift 표 작성** (§0) — 소스(엔티티 코드 또는 체크리스트)와 기존 ERD 대조.
3. 테이블별 **필드 표** 작성: 한글명 | DB컬럼 | 타입(§5) | 제약(§6). **테이블 height = 30+30×N 계산.**
4. **관계 표** 작성/갱신 (§0/§7) → 누락/오방향 엣지 식별.
5. **좌표표 작성** (§9 배치, 높이 반영) → **§2.4 충돌 전수 검사 통과**.
6. **엣지 anchor 표 작성** → 각 테이블별 (변,분수) 중복 없는지 §4.3 확인, exit/entry 4속성 모두 채움 (§4.2).
7. XML 작성/수정 (Write 또는 Read→Edit).
8. 범례·구분선(있다면) 유지 확인.
9. 자가 검증 (§11).

---

## 11. 자가 검증

```bash
# (1) ERD 의 shape=table 수 확인 (범례 예제 테이블 1개 포함)
grep -oE 'shape=table;' docs/diagram/ERD.drawio | wc -l
# → §0 "현재 테이블 체크리스트" 행 수 + 1(범례) 과 일치해야 함

# (2) 엔티티 코드가 있다면, 거기 정의된 테이블명이 ERD 헤더에 모두 있는지
grep -rl '@Entity' src/main/java 2>/dev/null
#  있으면: 각 @Entity 의 @Table(name=...) / 클래스명 ↔ ERD 헤더 "한글명(영문명)" 의 영문명 대조
#  없으면: §0 체크리스트가 유일한 기준 — 그 표와 ERD 헤더를 눈으로 대조

# (3) 좌표가 격자(10의 배수)인지 — mxPoint/mxGeometry 실좌표만 (vertex="1" 오탐 제외)
grep -oE '<mx(Point|Geometry|Rectangle)[^>]*' docs/diagram/ERD.drawio \
  | grep -oE '(^|[ ])(x|y)="[0-9]+"' | grep -vE '0"$'

# (4) 모든 관계 엣지에 crow's-foot 화살표가 있는지
grep -E 'edge="1"' docs/diagram/ERD.drawio | grep -v 'Arrow=ER' | grep -v 'dashed=1'

# (5) 관계 엣지에 exit/entry 가 명시됐는지 (자동 라우팅 잔재 탐지)
grep -E 'Arrow=ER' docs/diagram/ERD.drawio | grep -v 'exitX'
```

- (1) 개수 불일치 → 테이블 누락/잉여.
- (4) 비-점선 edge 인데 `Arrow=ER` 없음 → crow's-foot 누락 (범례 점선은 제외).
- (5) 출력되면 → exit/entry 없는 엣지 = 자동 라우팅 위험 (§4.2). 범례의 카디널리티 예시
  마커(id 21/24/27/30 류, 실제 테이블을 잇지 않는 장식용 점-선-점)는 예외 — 실제 관계 엣지
  (현재 user↔dna_info, user↔diary)는 둘 다 통과해야 한다. 새로 추가하는 엣지도 처음부터
  exit/entry 를 채워 넣을 것.
- §0 관계 체크리스트의 모든 관계가 엣지로 존재하는지 수동 확인.

**겹침/앵커/관통 검증은 가능하면 코드로, 안 되면 손으로 좌표표를 대조해서라도 반드시 한다
(수동 눈검사만으로 넘기지 말 것)**:
- **AABB 겹침**(§2.4): 모든 테이블 쌍이 H/V gutter 이상 떨어졌는지.
- **앵커 중복**(§4.3): 테이블별 (변,분수) 전부 distinct.
- **엣지 관통**: 폴리라인(exit점→waypoints→entry점)이 양 끝 아닌 테이블 내부를 지나지 않는지.
- **포개짐(collinear)**: 서로 다른 엣지의 같은-`x` 수직 / 같은-`y` 수평 세그먼트가 구간 중첩하는지
  (점 교차는 허용, 구간 겹침만 실패).
- 테이블/관계 수가 늘어 손 검증이 버거워지면(대략 6~8개 테이블 이상) §서두의 생성 스크립트
  도입을 다시 고려한다.

---

## 12. 수정 작업 주의

- 테이블 행 추가/삭제 시 **헤더 height (`30+30×N`) 와 모든 하위 행 `y` 재계산** + §2.4 충돌 재검사.
- 테이블 한 개 이동 시 **연결된 모든 엣지 waypoint·exit/entry** 동반 갱신 (안 그러면 엣지가 박스 통과/머리 어긋남).
- 필드 순서는 도메인 상 자연스러운 순서 유지 (PK → FK → 일반 → 타임스탬프류).
- 범례·구분선·depth 라벨은 데이터 테이블이 아니므로 자가 검증 (1)/(4) 에서 예외 처리.
- §0 의 "현재 테이블/관계 체크리스트" 와 "알려진 부채" 는 이 스킬의 실질적 소스다 — 테이블/관계를
  바꿨는데 그 표를 안 고치면 다음 세션에서 drift 가 재발한다.

---

## 13. Appendix — 흔한 실수 / 보강 메모

1. **stale ERD 신뢰** — 소스(엔티티 코드 또는 §0 체크리스트)와 실제로 대조하지 않고 "ERD 에
   있으니 맞겠지" 로 넘어감. 항상 §0 기준으로 확인.
2. **가변 높이 무시** — 테이블 height 를 고정값으로 잡아 필드 많은 테이블(`dna_info`, 15행)이
   아래/옆 테이블을 침범. §2.3.
3. **충돌 검증 생략** — 빈 곳에 눈대중 배치 → 엔티티 겹침. §2.4 좌표표 전수 검사 필수.
4. **엣지 머리 한 점에 몰림** — exit/entry 분산 안 함 → 머리 포개짐. §4.3.
5. **간격을 IA 모듈로 잡음** — 폭 400 테이블에 작은 gutter → 빽빽함. §2.2 의 큰 상수 사용.
6. **자동 라우팅 신뢰** — exit/entry 미명시 → 엣지가 임의 지점 출발/박스 통과. §4.2, §4.5.
   (이 ERD 의 관계 엣지 2개도 한때 이 상태였다 — waypoint 좌표에서 역산해 채워 넣고 해결.)
7. **관계 필드를 컬럼 행으로 그림** — JPA 의 연관관계 필드 자체(`User user`)는 컬럼이 아니다.
   실제 DB 컬럼인 FK 스칼라(`author_id` 등)만 행으로 그린다.
8. **카디널리티 혼용** — optional FK 인데 부모 쪽을 `ERone` 으로 잘못 표기. §7 표로 통일.
9. **복합 PK 누락** — 복합키 테이블이 생기면 관련 컬럼 모두 `PK` 로 표기해야 함.
10. **누락 관계** — 배열 없는 단방향 관계(예: PK 공유 1:1)를 빠뜨리기 쉽다. §0 체크리스트로 전수 확인.
11. **height 미갱신** — 행 추가했는데 테이블 height 그대로 → 마지막 행이 잘려 보임.
12. **손 XML 의 한계** — 지금은 3테이블이라 괜찮지만, 규모가 커지면 좌표/높이/앵커가 반드시
    어긋난다. 데이터 배열 → 직렬화 → 코드 자가검증 방식의 생성 스크립트 도입을 그때 고려.
13. **격자 grep 오탐** — `(x|y)="\d+"` 는 `vertex="1"` 의 `x="1"` 을 잡는다. §11 (3) 처럼
    `<mxPoint|mxGeometry|mxRectangle>` 안의 좌표만 매칭할 것.
14. **waypoint 를 entry/exit 분수 픽셀에 맞추다 격자 깨짐** — waypoint 는 독립적으로 10배수로
    두고, entry 점까지의 수 px 단차는 무시(시각적으로 안 보임). 또는 측면 진입 대신 상/하
    진입으로 바꿔 y 를 밴드 경계(격자)에 맞춘다.
15. **밴드 간 gap 에 엣지가 몰림** — 허브 테이블 아래 자식 엣지들이 같은 gap 에 모이기 쉽다.
    lane 을 서로 다른 좌표로 분산.
