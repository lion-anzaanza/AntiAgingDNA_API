package cloud.anzaanza.antiagingdna.entity;

import cloud.anzaanza.antiagingdna.entity.enums.CaffeineCups;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineLastTime;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseDuration;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseType;
import cloud.anzaanza.antiagingdna.entity.enums.MoodRecovery;
import cloud.anzaanza.antiagingdna.entity.enums.ScreenTime;
import cloud.anzaanza.antiagingdna.entity.enums.SittingHours;
import cloud.anzaanza.antiagingdna.entity.enums.SleepLatency;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContact;
import cloud.anzaanza.antiagingdna.entity.enums.SugarIntake;
import cloud.anzaanza.antiagingdna.entity.enums.WalkDuration;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 오늘의 일지 — 하루 1건의 EMA(실시간 순간기록).
 *
 * <p>채점 항목은 전부 nullable 이다. 미입력은 0점이 아니라 <b>결측</b>으로, 영역 집계에서
 * 제외한 뒤 재정규화한다(기획 일지 §5). 따라서 "입력하지 않음"과 "0으로 입력함"을 DB 수준에서
 * 구분할 수 있어야 한다.
 *
 * <p>{@link #conditionLevel}(오늘의 컨디션)은 종속변수다. 종합점수 <b>합산에 넣지 않는다</b>
 * — 행동 지수가 예측·검증해야 할 결과값이므로 합산에 넣으면 순환논리가 된다(기획 §1①).
 *
 * <p><b>Phase 2 컬럼</b> — 기획 확정 · 목업 {@code 01_CREATE_DIARY} 미반영:
 * 카페인 잔 수 · 카페인 시각 · 기분 회복 활동 · 사람 만남 · 걸은 시간.
 *
 * <p>enum 컬럼의 {@code @JdbcTypeCode(SqlTypes.VARCHAR)} 에 대해서는 {@link DnaInfo} 참고.
 */
@Entity
@Table(
        name = "diary",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_diary_author_log_date",
                columnNames = {"author_id", "log_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Diary extends BaseTimeEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "author_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_diary_author"))
    private User author;

    /**
     * 기록 대상 날짜. 작성 시각({@code created_at})과 다르다 — 자정 넘겨 어제 일지를 쓸 수 있다.
     * 1인 1일 1건 제약과 7일 이동평균의 기준이 되는 축이다.
     */
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    // ── 결과변수 ──────────────────────────────────────────────────
    /** 오늘의 컨디션 1(매우 나쁨) ~ 5(매우 좋음). 종속변수 — 종합점수 합산에서 제외 */
    @Column(name = "condition_level", nullable = false)
    private Integer conditionLevel;

    // ── 수면 ─────────────────────────────────────────────────────
    // 수면 시간은 두 시각에서 파생되는 값이라 컬럼으로 저장하지 않는다
    // (기획 일지 §2 "Ⓘ 시각 입력 → 자동 계산"). 자정을 넘긴 수면의 해석 규칙은 채점 계층에 둔다.
    @Column(name = "sleep_started_at")
    private LocalTime sleepStartedAt;

    @Column(name = "sleep_ended_at")
    private LocalTime sleepEndedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sleep_latency", length = 32)
    private SleepLatency sleepLatency;

    /** 수면 만족도 1(뒤척임) ~ 5(푹잠) */
    @Column(name = "sleep_satisfaction")
    private Integer sleepSatisfaction;

    // ── 식·수분 ──────────────────────────────────────────────────
    /** 패스트푸드·디저트·가당음료 섭취 횟수. 감점분에 k_sugar 를 곱한다 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sugar_intake", length = 32)
    private SugarIntake sugarIntake;

    /** 카페인 잔 수. 감점분에 k_caffeine 을 곱한다 (Phase 2) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "caffeine_cups", length = 32)
    private CaffeineCups caffeineCups;

    /** 카페인 마지막 섭취 시각대. 양과 독립적인 감점 요인이라 별도 컬럼이다 (Phase 2) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "caffeine_last_time", length = 32)
    private CaffeineLastTime caffeineLastTime;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "water_intake", length = 32)
    private WaterIntake waterIntake;

    // ── 활동 ─────────────────────────────────────────────────────
    /** 운동 여부. false 면 시간·종류는 null 이고 신체활동 점수는 0점 처리(결측 아님) */
    @Column(name = "exercised")
    private Boolean exercised;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exercise_duration", length = 32)
    private ExerciseDuration exerciseDuration;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exercise_type", length = 32)
    private ExerciseType exerciseType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sitting_hours", length = 32)
    private SittingHours sittingHours;

    // ── 정신·감정 ────────────────────────────────────────────────
    /**
     * 스트레스 지수 1~10 (0~10 NRS 표준). 정규화는 {@code s = 100 × (10 − x) / 9} 후 k_stress 적용.
     * 목업의 0~100 슬라이더가 아니다 — 기획 §2 가 슬라이더 제거를 명시했다.
     */
    @Column(name = "stress_level")
    private Integer stressLevel;

    /** 기분 전환·회복 활동. 감정 영역의 두 번째 소스 (Phase 2) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mood_recovery", length = 32)
    private MoodRecovery moodRecovery;

    // ── 사회 ─────────────────────────────────────────────────────
    /** 사람 만남. 사회 영역 당일 점수의 유일한 소스 (Phase 2) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "social_contact", length = 32)
    private SocialContact socialContact;

    // ── 참고 항목 (비채점) ───────────────────────────────────────
    /** 오늘 식사 횟수 0~5 (5 = 5끼 이상). 건강 점수 표준이 없어 패턴 참고용으로만 기록 */
    @Column(name = "meal_count")
    private Integer mealCount;

    /** 걸은 시간. 별도 표준이 없고 운동 항목과 중복돼 비채점 (Phase 2) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "walk_duration", length = 32)
    private WalkDuration walkDuration;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "screen_time", length = 32)
    private ScreenTime screenTime;

    /**
     * 같은 날짜의 일지를 새 입력으로 <b>통째로</b> 교체한다. {@code id}·{@code author}·
     * {@code logDate}·감사 컬럼은 유지된다.
     *
     * <p>부분 갱신(PATCH)이 아니라 전체 교체다. 미입력({@code null})은 "안 바꿈"이 아니라
     * "지움"이어야 한다 — 잘못 고른 항목을 되돌릴 방법이 있어야 하고, 채점에서 결측과 값의
     * 구분이 의미를 갖기 때문이다.
     *
     * <p>세터를 열지 않고 이 메서드 하나만 두는 이유는, 바뀔 수 있는 시점을 한 곳으로
     * 모아두기 위해서다. 필드를 추가하고 여기 복사를 빠뜨리는 사고는
     * {@code DiaryReplaceCoverageTest} 가 막는다.
     */
    public void replaceWith(Diary source) {
        this.conditionLevel = source.conditionLevel;
        this.sleepStartedAt = source.sleepStartedAt;
        this.sleepEndedAt = source.sleepEndedAt;
        this.sleepLatency = source.sleepLatency;
        this.sleepSatisfaction = source.sleepSatisfaction;
        this.sugarIntake = source.sugarIntake;
        this.caffeineCups = source.caffeineCups;
        this.caffeineLastTime = source.caffeineLastTime;
        this.waterIntake = source.waterIntake;
        this.exercised = source.exercised;
        this.exerciseDuration = source.exerciseDuration;
        this.exerciseType = source.exerciseType;
        this.sittingHours = source.sittingHours;
        this.stressLevel = source.stressLevel;
        this.moodRecovery = source.moodRecovery;
        this.socialContact = source.socialContact;
        this.mealCount = source.mealCount;
        this.walkDuration = source.walkDuration;
        this.screenTime = source.screenTime;
    }
}
