package cloud.anzaanza.antiagingdna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 일별 종합점수 스냅샷 — 사용자 × 날짜 1행.
 *
 * <p>{@link Diary} 에 점수 컬럼을 붙이지 않고 테이블을 분리한 이유:
 * <ul>
 *   <li>가입 당일(day-0)은 일지가 0건인데도 온보딩 baseline 만으로 점수 행이 필요하다.</li>
 *   <li>일지 미작성일에도 표시 점수(baseline 결합 + 7일 이동평균)는 존재한다.</li>
 *   <li>재채점({@link #scoringVersion} 변경)이 원본 기록을 건드리지 않는다.</li>
 *   <li>{@code α(n) = n/(n+7)} 의 n(영역별 기록 일수)이 영역 점수 컬럼의 non-null 개수로
 *       바로 집계된다.</li>
 * </ul>
 *
 * <p>영역 점수는 <b>당일 일지만으로 산출한 값</b>이다(baseline 결합 전). 결합값
 * {@code C_c(t) = (1−α)·baseline_c + α·mean7(C_c)} 은 이 행들로부터 재계산 가능하므로
 * 저장하지 않고, 최종 표시값 {@link #displayTotal} 만 캐시한다.
 */
@Entity
@Table(
        name = "daily_score",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_score_user_date",
                columnNames = {"user_id", "score_date"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DailyScore {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_score_user"))
    private User user;

    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate;

    // ── 영역별 당일 점수 (0~100). 해당 영역에 기록된 항목이 하나도 없으면 null ──
    /** 신체 — 수면·식이·활동 */
    @Column(name = "physical_score", precision = 5, scale = 2)
    private BigDecimal physicalScore;

    /** 정신 — 수면·스트레스·규칙성 */
    @Column(name = "mental_score", precision = 5, scale = 2)
    private BigDecimal mentalScore;

    /** 감정 — 스트레스. 보류 항목(기분 회복 활동)이 들어오기 전까지 소스가 1개뿐이다 */
    @Column(name = "emotion_score", precision = 5, scale = 2)
    private BigDecimal emotionScore;

    /**
     * 사회 — 일지 '사람 만남' 단일 소스. 해당 항목이 Phase 2 라 1차 구현에서는 항상 null 이다.
     */
    @Column(name = "social_score", precision = 5, scale = 2)
    private BigDecimal socialScore;

    /**
     * 환경 — Phase 1 미사용. 기획 원안은 상수 50 에 가중치 0.10 이었으나, 모든 사용자에게
     * 동일하게 붙는 상수라 점수 변별력에 기여하지 않고 나머지 영역의 해상도만 깎는다.
     * 원안(30/25/20/15/10)에서 환경을 빼고 나머지를 재정규화한 값을 쓴다.
     * 실제 환경 신호를 받게 되면 그때 이 컬럼과 {@code scoring.weights.environment} 를 켠다.
     */
    @Column(name = "environment_score", precision = 5, scale = 2)
    private BigDecimal environmentScore;

    // ── 종합 ─────────────────────────────────────────────────────
    /** 당일 영역 가중합 (baseline 결합 전). 일지가 없는 날은 null */
    @Column(name = "daily_total", precision = 5, scale = 2)
    private BigDecimal dailyTotal;

    /** 화면에 표시하는 종합점수 — baseline 결합 후 7일 이동평균까지 적용한 값 */
    @Column(name = "display_total", precision = 5, scale = 2, nullable = false)
    private BigDecimal displayTotal;

    /**
     * 이 행을 산출한 채점 파라미터의 버전 태그 ({@code scoring.version} 설정값).
     * Phase 2 재보정 시 과거 점수와 신규 점수를 섞지 않기 위한 격리 축이다.
     */
    @Column(name = "scoring_version", length = 32, nullable = false)
    private String scoringVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
