package cloud.anzaanza.antiagingdna.entity;

import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContactLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 초기 진단(온보딩) 응답 — 회원가입 STEP 3. 사용자당 1행이며 {@code user} 와 PK 를 공유한다.
 *
 * <p><b>이 테이블은 원본 답변만 담는다.</b> 영역별 baseline 점수·민감도 계수·종합점수는 전부
 * 여기서 파생되는 값이므로 저장하지 않는다. 기획상 정규화 앵커와 영역 가중치가 모두 "초안"
 * 이고 Phase 2 에 재보정될 예정이라, 파생값을 원본과 같은 행에 섞으면 재보정 시점에 어떤
 * 행이 어떤 파라미터로 계산됐는지 복원할 수 없다. 파생 점수는
 * {@link DailyScore} 가 {@code scoring_version} 과 함께 보관한다.
 *
 * <p><b>Phase 2 컬럼</b> — 기획에는 확정돼 있으나 목업 {@code 02_INITIAL_DIAGNOSIS} 에
 * 반영되지 않아 nullable 로 둔 항목: 사회 교류(Q11) · WHO-5 웰빙 5문항(Q12).
 * 이 둘이 비어 있으면 사회 영역 baseline 의 소스가 0개가 되고 감정 영역은 생활 리듬 하나로만
 * 산출된다. 화면 확정 시 {@code nullable = false} 로 조이는 것을 검토할 것.
 *
 * <p>enum 컬럼에 {@code @JdbcTypeCode(SqlTypes.VARCHAR)} 를 붙인 이유 — MySQLDialect 는
 * {@code @Enumerated(STRING)} 을 <b>네이티브 {@code enum('A','B',...)} 타입</b>으로 매핑한다.
 * 그러면 선택지 목록이 컬럼 정의에 박혀서, 상수를 하나 추가할 때마다 ALTER 마이그레이션이
 * 필요해진다. 기획 선택지가 이미 9차례 개정된 도메인이라 VARCHAR 로 못박는다.
 */
@Entity
@Table(name = "dna_info")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DnaInfo {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", foreignKey = @ForeignKey(name = "fk_dna_info_user"))
    private User user;

    // ── Q1. 수면 유형 (파라미터 문항) ──────────────────────────────
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sleep_type", length = 32, nullable = false)
    private SleepType sleepType;

    // ── Q2. 수면 질 자가진단 (다중 선택 · PSQI/Epworth) ────────────
    // 체크 수가 곧 위험도다: baseline = 100 − 25 × 체크수 (기획 §A-1).
    // 체크박스마다 컬럼을 쪼갠 이유 — 선택지가 늘어도 ALTER ADD COLUMN 으로 흡수되고,
    // 비트마스크와 달리 어떤 증상이 몇 명에게 있는지 SQL 로 바로 집계된다.
    // 4개 전부 false = "해당 없음".

    /** 낮에 졸림이 잦다 */
    @Column(name = "sleep_daytime_drowsy", nullable = false)
    private boolean sleepDaytimeDrowsy;

    /** 잠들기까지 30분 이상 걸린다 */
    @Column(name = "sleep_onset_delayed", nullable = false)
    private boolean sleepOnsetDelayed;

    /** 자다가 자주 깬다 */
    @Column(name = "sleep_night_awakening", nullable = false)
    private boolean sleepNightAwakening;

    /** 자도 개운치 않다 */
    @Column(name = "sleep_unrefreshed", nullable = false)
    private boolean sleepUnrefreshed;

    // ── Q3~Q5. 민감도 (계수 문항) ─────────────────────────────────
    /** 당분 민감도 → k_sugar */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sugar_sensitivity", length = 32, nullable = false)
    private SensitivityLevel sugarSensitivity;

    /** 카페인 민감도 → k_caffeine. 곱할 대상은 {@code diary.caffeine_cups} / {@code caffeine_last_time}(둘 다 Phase 2) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "caffeine_sensitivity", length = 32, nullable = false)
    private SensitivityLevel caffeineSensitivity;

    /** 스트레스 민감도 → k_stress */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "stress_sensitivity", length = 32, nullable = false)
    private SensitivityLevel stressSensitivity;

    // ── Q6~Q10. baseline / 플래그 문항 ────────────────────────────
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "exercise_level", length = 32, nullable = false)
    private ExerciseLevel exerciseLevel;

    // Q7 은 다중 선택이다 — 교대근무와 시차는 별개 사유이므로 한 컬럼으로 합칠 수 없다.
    // 둘 다 false = "해당 없음". 감점이 아니라 수면 규칙성 기준을 완화하는 리스크 플래그다 (기획 §1③).

    /** 교대·야간근무 */
    @Column(name = "is_shift_worker", nullable = false)
    private boolean shiftWorker;

    /** 잦은 출장·시차 */
    @Column(name = "is_frequent_traveler", nullable = false)
    private boolean frequentTraveler;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "drink_frequency", length = 32, nullable = false)
    private DrinkFrequency drinkFrequency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "smoking_status", length = 32, nullable = false)
    private SmokingStatus smokingStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "life_rhythm", length = 32, nullable = false)
    private LifeRhythm lifeRhythm;

    // ── Q11~Q12 (Phase 2 · 목업 미반영이라 nullable) ──────────────
    /** Q11. 사회 영역 baseline 의 유일한 소스 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "social_contact_level", length = 32)
    private SocialContactLevel socialContactLevel;

    // Q12. WHO-5 웰빙 지수 — 각 문항 0~5, 합산 × 4 로 0~100 환산(채점 계층).
    // 기획 §7⑤ 가 풀버전(5문항)을 확정했다 — 단일문항 축약은 WHO-5 의 타당도를 잃는다.
    // 문항 내용은 기획에 목록이 없어 컬럼명에 담지 않고 번호로만 둔다.

    /** 문항 1 */
    @Column(name = "who5_q1")
    private Integer who5Q1;

    /** 문항 2 */
    @Column(name = "who5_q2")
    private Integer who5Q2;

    /** 문항 3 */
    @Column(name = "who5_q3")
    private Integer who5Q3;

    /** 문항 4 */
    @Column(name = "who5_q4")
    private Integer who5Q4;

    /** 문항 5 */
    @Column(name = "who5_q5")
    private Integer who5Q5;

    // ── 감사 컬럼 ─────────────────────────────────────────────────
    /** 진단 완료 시각 = 이 행의 생성 시각 */
    @CreatedDate
    @Column(name = "completed_at", nullable = false, updatable = false)
    private LocalDateTime completedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
