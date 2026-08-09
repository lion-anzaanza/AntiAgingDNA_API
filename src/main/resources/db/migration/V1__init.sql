-- LifeDNA 초기 스키마
--
-- 이 파일이 스키마의 단일 소스다 (spring.jpa.hibernate.ddl-auto=validate).
-- 컬럼 구성은 docs/diagram/ERD.drawio 와 1:1 이며, SchemaGenerationTest 가 엔티티 매핑과
-- 이 파일을 대조해 어긋나면 빌드를 깬다.
--
-- 타입은 Hibernate MySQLDialect 가 엔티티에서 생성하는 것과 정확히 같게 맞춰야 한다.
-- (datetime(6) / bit / time(0) / integer) — 다르면 validate 가 부팅을 거부한다.
--
-- Phase 2 로 표시된 컬럼은 기획에는 확정돼 있으나 목업(docs/ui)에 미반영이라 nullable 이다.

-- ── 사용자 ─────────────────────────────────────────────────────
create table user (
    id          varchar(64)  not null,
    email       varchar(255) not null comment '로그인 식별자',
    password    varchar(255) not null comment 'BCrypt 해시',
    nickname    varchar(32)  not null,
    birth_year  integer      not null comment '출생연도 4자리',
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

alter table user add constraint uk_user_email unique (email);

-- ── 약관 동의 (목업 03_TERMS 의 항목 4개) ──────────────────────
create table user_agreement (
    id              varchar(64) not null,
    user_id         varchar(64) not null,
    agreement_type  varchar(32) not null comment 'TERMS_OF_SERVICE / PRIVACY_SENSITIVE / MARKETING / AGE_OVER_14',
    agreed          bit         not null comment '현재 동의 상태 (마케팅은 철회 가능)',
    agreed_at       datetime(6) not null comment '이 상태로 마지막으로 바뀐 시각',
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

alter table user_agreement add constraint uk_user_agreement_user_type unique (user_id, agreement_type);
alter table user_agreement add constraint fk_user_agreement_user foreign key (user_id) references user (id);

-- ── 초기 진단 (온보딩 12문항 원본 답변) ────────────────────────
-- user 와 PK 를 공유하는 1:1 (@MapsId) — 별도 FK 컬럼이 없다.
create table dna_info (
    id                     varchar(64) not null,
    sleep_type             varchar(32) not null comment 'Q1 크로노타입 (MEQ)',
    sleep_daytime_drowsy   bit         not null comment 'Q2 PSQI 체크',
    sleep_onset_delayed    bit         not null comment 'Q2 PSQI 체크',
    sleep_night_awakening  bit         not null comment 'Q2 PSQI 체크',
    sleep_unrefreshed      bit         not null comment 'Q2 PSQI 체크 (4개 전부 false = 해당 없음)',
    sugar_sensitivity      varchar(32) not null comment 'Q3 → k_sugar',
    caffeine_sensitivity   varchar(32) not null comment 'Q4 → k_caffeine',
    stress_sensitivity     varchar(32) not null comment 'Q5 → k_stress',
    exercise_level         varchar(32) not null comment 'Q6 WHO 분/주',
    is_shift_worker        bit         not null comment 'Q7 리스크 플래그',
    is_frequent_traveler   bit         not null comment 'Q7 리스크 플래그',
    drink_frequency        varchar(32) not null comment 'Q8 AUDIT-C 소비빈도',
    smoking_status         varchar(32) not null comment 'Q9 KNHANES 4분류',
    life_rhythm            varchar(32) not null comment 'Q10',
    social_contact_level   varchar(32)          comment 'Q11 (Phase 2) 사회 영역 baseline 의 유일한 소스',
    who5_q1                integer              comment 'Q12 WHO-5 (Phase 2) 각 0~5',
    who5_q2                integer              comment 'Q12 WHO-5 (Phase 2)',
    who5_q3                integer              comment 'Q12 WHO-5 (Phase 2)',
    who5_q4                integer              comment 'Q12 WHO-5 (Phase 2)',
    who5_q5                integer              comment 'Q12 WHO-5 (Phase 2)',
    completed_at           datetime(6) not null comment '진단 완료 시각',
    updated_at             datetime(6) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

alter table dna_info add constraint fk_dna_info_user foreign key (id) references user (id);

-- ── 오늘의 일지 (EMA) ──────────────────────────────────────────
-- 채점 항목은 전부 nullable — 미입력은 0점이 아니라 결측이다(기획 일지 §5).
create table diary (
    id                  varchar(64) not null,
    author_id           varchar(64) not null,
    log_date            date        not null comment '기록 대상 날짜 (작성 시각과 다르다)',
    condition_level     integer     not null comment '오늘의 컨디션 1~5. 종속변수 — 종합점수 합산 제외',
    sleep_started_at    time(0)              comment '취침 시각',
    sleep_ended_at      time(0)              comment '기상 시각 (수면 시간은 파생값이라 저장하지 않음)',
    sleep_latency       varchar(32)          comment '잠들기까지 (PSQI)',
    sleep_satisfaction  integer              comment '수면 만족도 1~5',
    sugar_intake        varchar(32)          comment '× k_sugar',
    caffeine_cups       varchar(32)          comment '(Phase 2) × k_caffeine',
    caffeine_last_time  varchar(32)          comment '(Phase 2) 타이밍 감점 (Drake 2013)',
    water_intake        varchar(32)          comment '하한형 (EFSA)',
    exercised           bit                  comment 'false 면 시간·종류는 null',
    exercise_duration   varchar(32),
    exercise_type       varchar(32)          comment '비채점 — 기획 §8 에 종류별 앵커가 없다',
    sitting_hours       varchar(32)          comment '역방향 단조 (U자 아님)',
    stress_level        integer              comment '1~10 NRS. × k_stress',
    mood_recovery       varchar(32)          comment '(Phase 2) 앵커 미확정이라 아직 비채점',
    social_contact      varchar(32)          comment '(Phase 2) 사회 영역 당일 점수의 유일한 소스',
    meal_count          integer              comment '참고(비채점) 0~5, 5 = 5끼 이상',
    walk_duration       varchar(32)          comment '(Phase 2) 참고(비채점)',
    screen_time         varchar(32)          comment '참고(비채점) — 성인 화면시간 국제 표준 없음',
    created_at          datetime(6) not null,
    updated_at          datetime(6) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

alter table diary add constraint uk_diary_author_log_date unique (author_id, log_date);
alter table diary add constraint fk_diary_author foreign key (author_id) references user (id);

-- ── 일별 종합점수 (파생 캐시) ──────────────────────────────────
-- 기획이 저장을 요구하지는 않는다. α(n) 의 영역별 기록 일수, 7일 이동평균, 일지 0건인
-- day-0 점수가 실제로 요구해서 두는 read model 이다. 원본은 dna_info / diary 다.
create table daily_score (
    id                 varchar(64)  not null,
    user_id            varchar(64)  not null,
    score_date         date         not null,
    physical_score     decimal(5,2)          comment '당일 일지 기반 (baseline 결합 전). 소스 없으면 null',
    mental_score       decimal(5,2),
    emotion_score      decimal(5,2),
    social_score       decimal(5,2)          comment '소스가 Phase 2 라 1차 구현에서는 항상 null',
    environment_score  decimal(5,2)          comment '실 환경 신호 확보 전까지 미사용',
    daily_total        decimal(5,2)          comment '당일 영역 가중합',
    display_total      decimal(5,2) not null comment 'baseline 결합 + 7일 이동평균 후 표시값',
    scoring_version    varchar(32)  not null comment 'scoring.version 설정값. 재보정 이력 격리 축',
    created_at         datetime(6)  not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

alter table daily_score add constraint uk_daily_score_user_date unique (user_id, score_date);
alter table daily_score add constraint fk_daily_score_user foreign key (user_id) references user (id);
