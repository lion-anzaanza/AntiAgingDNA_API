#!/usr/bin/env node
/**
 * docs/diagram/ERD.drawio 생성기
 *
 * docs/diagram/SKILL.md §2~§4 규칙을 그대로 인코딩한다.
 *  - 테이블 height = 30 + 30 × 행수 (§2.2)
 *  - AABB 겹침 전수 검사, H_GUTTER/V_GUTTER (§2.4)
 *  - 앵커 (변, 분수) 중복 금지 (§4.3)
 *  - 엣지 폴리라인의 테이블 관통 / collinear 포개짐 검사 (§2.1-3, §4.5)
 *  - 메모 박스를 엣지가 관통하는지 검사 (ERD 전용 보강)
 * 검증에 실패하면 파일을 쓰지 않고 종료한다.
 *
 * 실행: node scripts/gen-erd.mjs
 *
 * 행 4번째 원소 뒤에 `true` 를 붙이면 Phase 2 행(옅은 배경)으로 그려진다.
 * Phase 1 = 기획 ∩ 목업 (1차 구현 확정) · Phase 2 = 기획에만 있고 목업 미반영.
 */
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'docs', 'diagram', 'ERD.drawio');

// ── §2.2 간격 상수 ──────────────────────────────────────────────
const TABLE_W = 400;
const HEADER_H = 30;
const ROW_H = 30;
const H_GUTTER = 240;
const V_GUTTER = 160;

// ── 스타일 (기존 ERD.drawio 에서 추출, §3) ──────────────────────
const FILL_HEADER = 'light-dark(#FFCC99,#663300)';
const FILL_HEADER_P2 = 'light-dark(#E8E8E8,#4A4A4A)';
const FILL_CELL_P2 = 'light-dark(#F5F5F5,#3C3C3C)';

const S_TABLE = (fill) =>
  `shape=table;startSize=30;container=1;collapsible=1;childLayout=tableLayout;fixedRows=1;rowLines=1;fontStyle=1;align=center;resizeLast=1;html=1;rounded=1;arcSize=14;absoluteArcSize=1;fillColor=${fill};horizontal=1;swimlaneFillColor=default;fontSize=12;`;
const S_ROW =
  'shape=tableRow;horizontal=0;startSize=0;swimlaneHead=0;swimlaneBody=0;fillColor=none;collapsible=0;dropTarget=0;points=[[0,0.5],[1,0.5]];portConstraint=eastwest;rounded=1;arcSize=14;absoluteArcSize=1;fontSize=12;fontStyle=1;';
const S_CELL = (align, fill) =>
  `shape=partialRectangle;connectable=0;fillColor=${fill};overflow=hidden;whiteSpace=wrap;html=1;rounded=1;arcSize=14;absoluteArcSize=1;${align};fontSize=12;fontStyle=1;`;
const S_EDGE = 'edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;';
const S_NOTE =
  'text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;rounded=0;fontSize=11;fontColor=#666666;';

const CELLS = [
  { x: 0, w: 100, align: 'align=center' },
  { x: 100, w: 160, align: 'align=left;spacingLeft=6' },
  { x: 260, w: 90, align: 'align=center' },
  { x: 350, w: 50, align: 'align=center' },
];

// ── 테이블 정의 ─────────────────────────────────────────────────
// row: [한글명, DB 컬럼, 타입(§5), 제약(§6), phase2?]
// 한글명은 셀 폭 100px 제약으로 8자 이내, DB 컬럼은 160px 제약으로 22자 이내.
const tables = [
  {
    key: 'user',
    header: '사용자(user)',
    x: 80,
    y: 80,
    rows: [
      ['고유ID', 'id', 'VARCHAR(64)', 'PK'],
      ['로그인ID', 'login_id', 'VARCHAR(32)', 'UQ'],
      ['이메일', 'email', 'VARCHAR(255)', 'UQ, NN'],
      ['비밀번호', 'password', 'VARCHAR(255)', 'NN'],
      ['닉네임', 'nickname', 'VARCHAR(32)', 'NN'],
      ['출생연도', 'birth_year', 'INT', 'NN'],
      ['생성 시각', 'created_at', 'DATETIME', 'NN'],
      ['수정 시각', 'updated_at', 'DATETIME', 'NN'],
    ],
  },
  {
    key: 'user_agreement',
    header: '약관 동의(user_agreement)',
    x: 1520,
    y: 80,
    rows: [
      ['아이디', 'id', 'VARCHAR(64)', 'PK'],
      ['사용자 아이디', 'user_id', 'VARCHAR(64)', 'FK, NN'],
      ['약관 종류', 'agreement_type', 'VARCHAR(32)', 'NN'],
      ['동의 여부', 'agreed', 'BOOLEAN', 'NN'],
      ['동의 시각', 'agreed_at', 'DATETIME', 'NN'],
    ],
  },
  {
    key: 'dna_info',
    header: 'DNA 정보(dna_info)',
    x: 80,
    y: 560,
    rows: [
      ['아이디', 'id', 'VARCHAR(64)', 'PK, FK'],
      ['수면 유형', 'sleep_type', 'VARCHAR(32)', 'NN'],
      ['낮 졸림', 'sleep_daytime_drowsy', 'BOOLEAN', 'NN'],
      ['입면 지연', 'sleep_onset_delayed', 'BOOLEAN', 'NN'],
      ['야간 각성', 'sleep_night_awakening', 'BOOLEAN', 'NN'],
      ['비회복 수면', 'sleep_unrefreshed', 'BOOLEAN', 'NN'],
      ['당분 민감도', 'sugar_sensitivity', 'VARCHAR(32)', 'NN'],
      ['카페인 민감도', 'caffeine_sensitivity', 'VARCHAR(32)', 'NN'],
      ['스트레스 민감도', 'stress_sensitivity', 'VARCHAR(32)', 'NN'],
      ['운동량', 'exercise_level', 'VARCHAR(32)', 'NN'],
      ['교대 근무', 'is_shift_worker', 'BOOLEAN', 'NN'],
      ['잦은 출장', 'is_frequent_traveler', 'BOOLEAN', 'NN'],
      ['음주 빈도', 'drink_frequency', 'VARCHAR(32)', 'NN'],
      ['흡연 상태', 'smoking_status', 'VARCHAR(32)', 'NN'],
      ['생활 리듬', 'life_rhythm', 'VARCHAR(32)', 'NN'],
      ['사회 교류', 'social_contact_level', 'VARCHAR(32)', '', true],
      ['웰빙 문항1', 'who5_q1', 'INT', '', true],
      ['웰빙 문항2', 'who5_q2', 'INT', '', true],
      ['웰빙 문항3', 'who5_q3', 'INT', '', true],
      ['웰빙 문항4', 'who5_q4', 'INT', '', true],
      ['웰빙 문항5', 'who5_q5', 'INT', '', true],
      ['진단 완료시각', 'completed_at', 'DATETIME', 'NN'],
      ['수정 시각', 'updated_at', 'DATETIME', 'NN'],
    ],
  },
  {
    key: 'diary',
    header: '일지(diary)',
    x: 800,
    y: 560,
    rows: [
      ['아이디', 'id', 'VARCHAR(64)', 'PK'],
      ['작성자 아이디', 'author_id', 'VARCHAR(64)', 'FK, NN'],
      ['기록 일자', 'log_date', 'DATE', 'NN'],
      ['오늘 컨디션', 'condition_level', 'INT', 'NN'],
      ['취침 시각', 'sleep_started_at', 'TIME', ''],
      ['기상 시각', 'sleep_ended_at', 'TIME', ''],
      ['잠들기까지', 'sleep_latency', 'VARCHAR(32)', ''],
      ['수면 만족도', 'sleep_satisfaction', 'INT', ''],
      ['당분 섭취', 'sugar_intake', 'VARCHAR(32)', ''],
      ['카페인 잔수', 'caffeine_cups', 'VARCHAR(32)', '', true],
      ['카페인 시각', 'caffeine_last_time', 'VARCHAR(32)', '', true],
      ['수분 섭취량', 'water_intake', 'VARCHAR(32)', ''],
      ['운동 여부', 'exercised', 'BOOLEAN', ''],
      ['운동 시간', 'exercise_duration', 'VARCHAR(32)', ''],
      ['운동 종류', 'exercise_type', 'VARCHAR(32)', ''],
      ['좌식 시간', 'sitting_hours', 'VARCHAR(32)', ''],
      ['스트레스 지수', 'stress_level', 'INT', ''],
      ['기분 회복활동', 'mood_recovery', 'VARCHAR(32)', '', true],
      ['사람 만남', 'social_contact', 'VARCHAR(32)', '', true],
      ['식사 횟수 ※', 'meal_count', 'INT', ''],
      ['걸은 시간 ※', 'walk_duration', 'VARCHAR(32)', '', true],
      ['화면 사용 ※', 'screen_time', 'VARCHAR(32)', ''],
      ['생성 시각', 'created_at', 'DATETIME', 'NN'],
      ['수정 시각', 'updated_at', 'DATETIME', 'NN'],
    ],
  },
  {
    key: 'daily_score',
    header: '일별 점수(daily_score)',
    x: 1520,
    y: 560,
    rows: [
      ['아이디', 'id', 'VARCHAR(64)', 'PK'],
      ['사용자 아이디', 'user_id', 'VARCHAR(64)', 'FK, NN'],
      ['기준 일자', 'score_date', 'DATE', 'NN'],
      ['신체 점수', 'physical_score', 'DECIMAL(5,2)', ''],
      ['정신 점수', 'mental_score', 'DECIMAL(5,2)', ''],
      ['감정 점수', 'emotion_score', 'DECIMAL(5,2)', ''],
      ['사회 점수', 'social_score', 'DECIMAL(5,2)', ''],
      ['환경 점수', 'environment_score', 'DECIMAL(5,2)', '', true],
      ['당일 종합', 'daily_total', 'DECIMAL(5,2)', ''],
      ['표시 종합', 'display_total', 'DECIMAL(5,2)', 'NN'],
      ['채점 버전', 'scoring_version', 'VARCHAR(32)', 'NN'],
      ['생성 시각', 'created_at', 'DATETIME', 'NN'],
    ],
  },
];

for (const t of tables) {
  t.w = TABLE_W;
  t.h = HEADER_H + ROW_H * t.rows.length; // §2.2 — 반드시 행 수로 계산
}
const T = Object.fromEntries(tables.map((t) => [t.key, t]));

// ── 관계 정의 (§0 관계 체크리스트와 1:1) ────────────────────────
// exit/entry 는 항상 4속성 명시 (§4.2). points 는 격자(10배수) lane (§4.5).
// user 는 허브라 아래 변에 4개를 0.2/0.4/0.6/0.8 로 등분한다 (§4.3, §4.4).
const edges = [
  // user_agreement 는 같은 밴드라 아래가 아니라 오른쪽 변으로 내보낸다 (§4.4 허브 분산)
  {
    from: 'user',
    to: 'user_agreement',
    cardinality: '1:N',
    // user 테이블이 login_id 행 추가로 8행(270px)이 됐다 — 0.5 는 더 이상 10배수 y 가 아니라
    // 130/270 으로 명시 (y=210).
    exit: [1, 130 / 270],
    entry: [0, 0.5],
    points: [[1000, 210], [1000, 170]],
  },
  { from: 'user', to: 'dna_info', cardinality: '1:1', exit: [0.2, 1], entry: [0.2, 0], points: [] },
  {
    from: 'user',
    to: 'diary',
    cardinality: '1:N',
    exit: [0.4, 1],
    entry: [0.25, 0],
    points: [[240, 400], [900, 400]],
  },
  {
    from: 'user',
    to: 'daily_score',
    cardinality: '1:N',
    exit: [0.6, 1],
    entry: [0.25, 0],
    points: [[320, 440], [1620, 440]],
  },
];

const ARROWS = {
  '1:1': 'endArrow=ERone;endFill=0;startArrow=ERone;startFill=0;',
  '1:N': 'endArrow=ERzeroToMany;endFill=0;startArrow=ERone;startFill=0;',
  '0..1:N': 'endArrow=ERzeroToMany;endFill=0;startArrow=ERzeroToOne;startFill=0;',
};

// ── 부속 메모 ───────────────────────────────────────────────────
const notes = [
  {
    x: 1520, y: 280, w: 400, h: 40,
    text: 'UNIQUE(user_id, agreement_type) — 사용자 × 약관 1행.&#xa;약관이 늘어도 컬럼 추가가 없고 항목별 동의 시각이 남는다.',
  },
  {
    x: 80, y: 1300, w: 400, h: 40,
    text: '온보딩 원본 답변만 보관. baseline·계수는 전부 파생값이라&#xa;저장하지 않는다 → daily_score 가 버전과 함께 보관.',
  },
  { x: 800, y: 1330, w: 400, h: 20, text: 'UNIQUE(author_id, log_date) — 1인 1일 1건' },
  {
    x: 800, y: 1370, w: 400, h: 40,
    text: '채점 항목은 전부 nullable — 미입력은 0점이 아니라 결측이다.&#xa;영역 집계에서 제외 후 재정규화(기획 일지 §5).',
  },
  { x: 1520, y: 970, w: 400, h: 20, text: 'UNIQUE(user_id, score_date) — 1인 1일 1행' },
  {
    x: 1520, y: 1010, w: 400, h: 60,
    text: '기획이 저장을 요구하진 않는다. α(n) 의 영역별 기록 일수 · 7일 이동평균 ·&#xa;일지 0건인 day-0 점수가 요구하는 파생 캐시(read model)다.&#xa;scoring_version 은 scoring.* 설정값의 태그 — FK 아님.',
  },
];

// ══ 검증 ═══════════════════════════════════════════════════════
const errors = [];

// (1) §2.4 AABB 겹침 전수 검사
for (let i = 0; i < tables.length; i++) {
  for (let j = i + 1; j < tables.length; j++) {
    const [a, b] = [tables[i], tables[j]];
    const ok =
      a.x + a.w + H_GUTTER <= b.x ||
      b.x + b.w + H_GUTTER <= a.x ||
      a.y + a.h + V_GUTTER <= b.y ||
      b.y + b.h + V_GUTTER <= a.y;
    if (!ok) errors.push(`[겹침] ${a.key} ↔ ${b.key} 가 gutter 미달`);
  }
}

// (2) §4.3 앵커 (변, 분수) 중복 검사
const anchors = new Map();
const sideOf = (frac) => (frac === 1 ? 'bottom' : frac === 0 ? 'top' : 'side');
const useAnchor = (key, side, frac, label) => {
  const k = `${key}|${side}|${frac}`;
  if (anchors.has(k)) errors.push(`[앵커중복] ${k} — ${anchors.get(k)} 와 ${label}`);
  anchors.set(k, label);
};
for (const e of edges) {
  useAnchor(e.from, sideOf(e.exit[1]), e.exit[0], `${e.from}→${e.to}`);
  useAnchor(e.to, sideOf(e.entry[1]), e.entry[0], `${e.from}→${e.to}`);
}

// (3) 폴리라인 구성 → 관통 / collinear 검사
const polyline = (e) => {
  const s = T[e.from];
  const t = T[e.to];
  const p0 = [s.x + s.w * e.exit[0], s.y + s.h * e.exit[1]];
  const p1 = [t.x + t.w * e.entry[0], t.y + t.h * e.entry[1]];
  const pts = [p0, ...e.points, p1];
  const out = [pts[0]];
  for (let i = 1; i < pts.length; i++) {
    const [px, py] = out[out.length - 1];
    const [cx, cy] = pts[i];
    if (px !== cx && py !== cy) out.push([px, cy]); // orthogonal 꺾임점 삽입
    out.push(pts[i]);
  }
  return out;
};

const segments = [];
for (const e of edges) {
  const pts = polyline(e);
  for (let i = 0; i < pts.length - 1; i++) {
    segments.push({ edge: `${e.from}→${e.to}`, a: pts[i], b: pts[i + 1], from: e.from, to: e.to });
  }
}

const segHitsRect = (a, b, r) => {
  const [x1, y1] = a;
  const [x2, y2] = b;
  return (
    Math.min(x1, x2) < r.x + r.w &&
    Math.max(x1, x2) > r.x &&
    Math.min(y1, y2) < r.y + r.h &&
    Math.max(y1, y2) > r.y
  );
};

// 3a. 테이블 관통
for (const s of segments) {
  for (const t of tables) {
    if (t.key === s.from || t.key === s.to) continue;
    if (segHitsRect(s.a, s.b, t)) errors.push(`[관통] ${s.edge} 세그먼트가 ${t.key} 를 통과`);
  }
}

// 3b. 메모 박스 관통 (엣지가 글씨를 가로지르면 읽을 수 없다)
notes.forEach((n, i) => {
  for (const s of segments) {
    if (segHitsRect(s.a, s.b, n)) errors.push(`[메모관통] ${s.edge} 가 메모#${i} (${n.x},${n.y}) 를 통과`);
  }
});

// 3c. collinear 포개짐 (§2.1-3)
const overlap = (a1, a2, b1, b2) => Math.min(a2, b2) - Math.max(a1, b1) > 0;
const vert = (s) => s.a[0] === s.b[0];
const horiz = (s) => s.a[1] === s.b[1];
for (let i = 0; i < segments.length; i++) {
  for (let j = i + 1; j < segments.length; j++) {
    const [p, q] = [segments[i], segments[j]];
    if (p.edge === q.edge) continue;
    if (vert(p) && vert(q) && p.a[0] === q.a[0]) {
      if (overlap(Math.min(p.a[1], p.b[1]), Math.max(p.a[1], p.b[1]), Math.min(q.a[1], q.b[1]), Math.max(q.a[1], q.b[1])))
        errors.push(`[포개짐] ${p.edge} / ${q.edge} 수직 x=${p.a[0]}`);
    }
    if (horiz(p) && horiz(q) && p.a[1] === q.a[1]) {
      if (overlap(Math.min(p.a[0], p.b[0]), Math.max(p.a[0], p.b[0]), Math.min(q.a[0], q.b[0]), Math.max(q.a[0], q.b[0])))
        errors.push(`[포개짐] ${p.edge} / ${q.edge} 수평 y=${p.a[1]}`);
    }
  }
}

// 3d. 메모 박스가 테이블/다른 메모와 겹치는지
const rectsOverlap = (a, b) =>
  a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
notes.forEach((n, i) => {
  for (const t of tables)
    if (rectsOverlap(n, t)) errors.push(`[메모겹침] 메모#${i} (${n.x},${n.y}) 가 ${t.key} 와 겹침`);
  notes.slice(i + 1).forEach((m, k) => {
    if (rectsOverlap(n, m)) errors.push(`[메모겹침] 메모#${i} / 메모#${i + 1 + k}`);
  });
});

// (4) 좌표 격자(10배수) — waypoint 와 앵커 접점 모두
for (const e of edges) {
  for (const [x, y] of e.points)
    if (x % 10 || y % 10) errors.push(`[격자] ${e.from}→${e.to} waypoint (${x},${y}) 가 10배수 아님`);
  const s = T[e.from];
  const t = T[e.to];
  const ax = s.x + s.w * e.exit[0];
  const ay = s.y + s.h * e.exit[1];
  const bx = t.x + t.w * e.entry[0];
  const by = t.y + t.h * e.entry[1];
  for (const [n, v] of [['exit x', ax], ['exit y', ay], ['entry x', bx], ['entry y', by]])
    if (v % 10) errors.push(`[격자] ${e.from}→${e.to} ${n}=${v} 가 10배수 아님 (분수 재배정 필요)`);
}

// (5) 셀 폭 초과 (overflow=hidden 이라 조용히 잘린다)
const MAX = [8, 22, 13, 6]; // 한글명 / DB컬럼 / 타입 / 제약
for (const t of tables)
  t.rows.forEach((r, i) => {
    for (let c = 0; c < 4; c++)
      if ((r[c] ?? '').length > MAX[c])
        errors.push(`[셀넘침] ${t.key} 행${i} 셀${c + 1} "${r[c]}" (${r[c].length} > ${MAX[c]})`);
  });

if (errors.length) {
  console.error('검증 실패 — 파일을 쓰지 않았다:');
  for (const m of errors) console.error('  ' + m);
  process.exit(1);
}

// ══ 직렬화 ═════════════════════════════════════════════════════
const esc = (s) =>
  String(s).replace(/&(?!#x?[0-9a-fA-F]+;)/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
let uid = 100;
const nextId = () => String(uid++);
const out = [];
const push = (s) => out.push(s);

const emitRows = (parentId, rows) => {
  rows.forEach((row, idx) => {
    const rowId = nextId();
    push(`        <mxCell id="${rowId}" parent="${parentId}" style="${S_ROW}" value="" vertex="1">`);
    push(`          <mxGeometry height="${ROW_H}" width="${TABLE_W}" y="${HEADER_H + ROW_H * idx}" as="geometry" />`);
    push(`        </mxCell>`);
    const fill = row[4] ? FILL_CELL_P2 : 'none';
    for (let ci = 0; ci < 4; ci++) {
      const c = CELLS[ci];
      push(`        <mxCell id="${nextId()}" parent="${rowId}" style="${S_CELL(c.align, fill)}" value="${esc(row[ci] ?? '')}" vertex="1">`);
      push(`          <mxGeometry height="${ROW_H}" width="${c.w}"${c.x ? ` x="${c.x}"` : ''} as="geometry">`);
      push(`            <mxRectangle height="${ROW_H}" width="${c.w}" as="alternateBounds" />`);
      push(`          </mxGeometry>`);
      push(`        </mxCell>`);
    }
  });
};

for (const t of tables) {
  t.id = nextId();
  push(`        <mxCell id="${t.id}" parent="1" style="${S_TABLE(t.phase2 ? FILL_HEADER_P2 : FILL_HEADER)}" value="${esc(t.header)}" vertex="1">`);
  push(`          <mxGeometry height="${t.h}" width="${t.w}" x="${t.x}" y="${t.y}" as="geometry" />`);
  push(`        </mxCell>`);
  emitRows(t.id, t.rows);
}

for (const e of edges) {
  const style =
    S_EDGE +
    `exitX=${e.exit[0]};exitY=${e.exit[1]};exitDx=0;exitDy=0;` +
    `entryX=${e.entry[0]};entryY=${e.entry[1]};entryDx=0;entryDy=0;` +
    ARROWS[e.cardinality];
  push(`        <mxCell id="${nextId()}" edge="1" parent="1" source="${T[e.from].id}" target="${T[e.to].id}" style="${style}">`);
  if (e.points.length) {
    push(`          <mxGeometry relative="1" as="geometry">`);
    push(`            <Array as="points">`);
    for (const [x, y] of e.points) push(`              <mxPoint x="${x}" y="${y}" />`);
    push(`            </Array>`);
    push(`          </mxGeometry>`);
  } else {
    push(`          <mxGeometry relative="1" as="geometry" />`);
  }
  push(`        </mxCell>`);
}

for (const n of notes) {
  push(`        <mxCell id="${nextId()}" parent="1" style="${S_NOTE}" value="${n.text}" vertex="1">`);
  push(`          <mxGeometry height="${n.h}" width="${n.w}" x="${n.x}" y="${n.y}" as="geometry" />`);
  push(`        </mxCell>`);
}

// ── 범례 (§9) — 좌표는 전부 격자에 정렬. dot 중심선 y 만 §13.14 예외 ──
const LEG = { tableX: 80, tableY: 1690, colX: 540, markerX: [540, 660], lineX: [550, 650] };
const legend = [];
legend.push(`        <mxCell id="2" parent="1" style="${S_TABLE(FILL_HEADER)}" value="엔티티(Entity)" vertex="1">`);
legend.push(`          <mxGeometry height="${HEADER_H + ROW_H * 3}" width="400" x="${LEG.tableX}" y="${LEG.tableY}" as="geometry" />`);
legend.push(`        </mxCell>`);
{
  const saved = out.length;
  emitRows('2', [
    ['아이디', 'id', 'VARCHAR(64)', 'PK'],
    ['속성명1', 'property', 'TYPE', ''],
    ['속성명2', 'property2', 'TYPE', ''],
  ]);
  legend.push(...out.splice(saved));
}
legend.push(`        <mxCell id="18" parent="1" style="text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;rounded=0;fontSize=14;fontStyle=1;" value="범례 (Legend)" vertex="1">`);
legend.push(`          <mxGeometry height="30" width="200" x="${LEG.colX}" y="1570" as="geometry" />`);
legend.push(`        </mxCell>`);
const markers = [
  { y: 1620, arrow: 'endArrow=ERzeroToMany;endFill=0;startArrow=ERone;startFill=0;' },
  { y: 1660, arrow: 'endArrow=ERone;endFill=0;startArrow=ERone;startFill=0;' },
  { y: 1700, arrow: 'endArrow=ERzeroToMany;endFill=0;startArrow=ERzeroToOne;startFill=0;' },
  { y: 1740, arrow: 'endArrow=ERone;endFill=0;startArrow=ERoneToMany;startFill=0;' },
];
for (const m of markers) {
  for (const mx of LEG.markerX) {
    legend.push(`        <mxCell id="${nextId()}" parent="1" style="ellipse;whiteSpace=wrap;html=1;fillColor=#666666;strokeColor=none;" value="" vertex="1">`);
    legend.push(`          <mxGeometry height="8" width="8" x="${mx}" y="${m.y}" as="geometry" />`);
    legend.push(`        </mxCell>`);
  }
  // 예시선 y = dot 중심 (dot.y+4) — 8px dot 특성상 격자에 못 맞춘다 (§13.14 예외)
  legend.push(`        <mxCell id="${nextId()}" edge="1" parent="1" style="${m.arrow}html=1;" value="">`);
  legend.push(`          <mxGeometry relative="1" as="geometry">`);
  legend.push(`            <mxPoint x="${LEG.lineX[0]}" y="${m.y + 4}" as="sourcePoint" />`);
  legend.push(`            <mxPoint x="${LEG.lineX[1]}" y="${m.y + 4}" as="targetPoint" />`);
  legend.push(`          </mxGeometry>`);
  legend.push(`        </mxCell>`);
}
for (const [y, text] of [
  [1790, 'PK — Primary Key (기본키)'],
  [1820, 'FK — Foreign Key (외래키)'],
  [1850, 'NN — Not Null (필수)'],
  [1880, 'UQ — Unique (고유)'],
]) {
  legend.push(`        <mxCell id="${nextId()}" parent="1" style="text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;rounded=0;fontSize=11;" value="${esc(text)}" vertex="1">`);
  legend.push(`          <mxGeometry height="20" width="280" x="${LEG.colX}" y="${y}" as="geometry" />`);
  legend.push(`        </mxCell>`);
}
// Phase 2 음영 설명
legend.push(`        <mxCell id="${nextId()}" parent="1" style="${S_CELL('align=center', FILL_CELL_P2)}" value="" vertex="1">`);
legend.push(`          <mxGeometry height="20" width="30" x="${LEG.colX}" y="1920" as="geometry" />`);
legend.push(`        </mxCell>`);
legend.push(`        <mxCell id="${nextId()}" parent="1" style="text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;rounded=0;fontSize=11;" value="Phase 2 — 기획 확정 · 목업 미반영 (1차 구현 보류)" vertex="1">`);
legend.push(`          <mxGeometry height="20" width="330" x="${LEG.colX + 40}" y="1920" as="geometry" />`);
legend.push(`        </mxCell>`);
legend.push(`        <mxCell id="${nextId()}" parent="1" style="text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;rounded=0;fontSize=11;" value="※ 참고 항목 — 국제 표준·역치가 없어 기록만 하고 채점에서 제외" vertex="1">`);
legend.push(`          <mxGeometry height="20" width="380" x="${LEG.colX}" y="1950" as="geometry" />`);
legend.push(`        </mxCell>`);

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<mxfile host="app.diagrams.net">
  <diagram name="페이지-1" id="nV7stiM0MMzQzXSHjIA1">
    <mxGraphModel dx="2080" dy="1215" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
${legend.join('\n')}
${out.join('\n')}
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
`;

writeFileSync(OUT, xml, 'utf8');
const p2Rows = tables.reduce((n, t) => n + t.rows.filter((r) => r[4]).length, 0);
console.log(`검증 통과 — ${OUT}`);
console.log(`  테이블 ${tables.length} / 관계 ${edges.length} / 데이터 행 ${tables.reduce((n, t) => n + t.rows.length, 0)} (Phase 2 ${p2Rows})`);
for (const t of tables)
  console.log(`  ${t.key.padEnd(18)} x=${String(t.x).padStart(4)} y=${String(t.y).padStart(4)} h=${String(t.h).padStart(3)} (${t.rows.length}행)${t.phase2 ? ' [Phase 2]' : ''}`);
