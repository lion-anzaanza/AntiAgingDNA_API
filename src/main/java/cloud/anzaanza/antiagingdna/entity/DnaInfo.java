package cloud.anzaanza.antiagingdna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dna_info")
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
    @JoinColumn(name = "id")
    private User user;

    @Column(name = "total_score")
    private Integer totalScore;

    // 정수 코드 컬럼 — 실제 값 목록 확정되면 Java enum + @Enumerated(ORDINAL) 로 교체 예정
    @Column(name = "sleep_type")
    private Integer sleepType;

    @Column(name = "sleep_quality")
    private Integer sleepQuality;

    @Column(name = "caffein_sensitivity")
    private Integer caffeinSensitivity;

    @Column(name = "insulin_sensitivity")
    private Integer insulinSensitivity;

    @Column(name = "stress_sensitivity")
    private Integer stressSensitivity;

    @Column(name = "weekly_exercise_frequency")
    private Integer weeklyExerciseFrequency;

    @Column(name = "is_smoker")
    private Boolean isSmoker;

    // 정수 코드 컬럼 — 실제 값 목록 확정되면 Java enum + @Enumerated(ORDINAL) 로 교체 예정
    @Column(name = "smoking_frequency")
    private Integer smokingFrequency;

    @Column(name = "is_shift_worker")
    private Boolean isShiftWorker;

    @Column(name = "is_drinker")
    private Boolean isDrinker;

    // 정수 코드 컬럼 — 실제 값 목록 확정되면 Java enum + @Enumerated(ORDINAL) 로 교체 예정
    @Column(name = "drink_frequency")
    private Integer drinkFrequency;

    // 정수 코드 컬럼 — 실제 값 목록 확정되면 Java enum + @Enumerated(ORDINAL) 로 교체 예정
    @Column(name = "life_rhythm")
    private Integer lifeRhythm;

    // 정수 코드 컬럼 — 실제 값 목록 확정되면 Java enum + @Enumerated(ORDINAL) 로 교체 예정
    @Column(name = "weekend_rhythm")
    private Integer weekendRhythm;
}
