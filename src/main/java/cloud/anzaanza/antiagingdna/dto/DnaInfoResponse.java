package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContactLevel;
import cloud.anzaanza.antiagingdna.service.scoring.AreaScores;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 내 LifeDNA — 초기 진단 원본 답변과 거기서 파생된 값.
 *
 * <p>파생값을 저장하지 않고 조회 시점에 계산해서 내려준다. {@code dna_info} 는 원본만 담는다는
 * 설계 전제(파라미터 재보정 시 과거 산출 근거를 복원할 수 있어야 한다)를 API 도 따른다.
 *
 * @param baseline 영역별 baseline (기획 §A-3). 일지가 쌓이기 전 점수의 출발점이다
 * @param sensitivityCoefficients 민감도 계수 k — 일지의 <b>감점분</b>에 곱해지는 개인화 축.
 *     선택지(Ⓐ 4점 척도)를 그대로 내려주는 {@code sensitivity} 와 짝이다
 */
public record DnaInfoResponse(
        LocalDateTime completedAt,
        SleepType sleepType,
        SleepIssues sleepIssues,
        SensitivityLevels sensitivity,
        ExerciseLevel exerciseLevel,
        WorkStyle workStyle,
        DrinkFrequency drinkFrequency,
        SmokingStatus smokingStatus,
        LifeRhythm lifeRhythm,
        SocialContactLevel socialContactLevel,
        List<Integer> who5,
        AreaScoreResponse baseline,
        SensitivityCoefficients sensitivityCoefficients) {

    /** 수면 질 자가진단 4항목. 체크 수가 곧 감점이다 (기획 §A-1 {@code 100 − 25 × 체크수}) */
    public record SleepIssues(
            boolean daytimeDrowsy, boolean onsetDelayed, boolean nightAwakening, boolean unrefreshed) {}

    /** 수면 규칙성 기준을 완화하는 리스크 플래그 (기획 §A-2). 감점 항목이 아니다 */
    public record WorkStyle(boolean shiftWorker, boolean frequentTraveler) {}

    public record SensitivityLevels(
            SensitivityLevel sugar, SensitivityLevel caffeine, SensitivityLevel stress) {}

    /** {@code s = 100 − k × (100 − s_raw)} 의 k */
    public record SensitivityCoefficients(Double sugar, Double caffeine, Double stress) {

        static SensitivityCoefficients of(DnaInfo dna) {
            return new SensitivityCoefficients(
                    coefficientOf(dna.getSugarSensitivity()),
                    coefficientOf(dna.getCaffeineSensitivity()),
                    coefficientOf(dna.getStressSensitivity()));
        }

        private static Double coefficientOf(SensitivityLevel level) {
            return level == null ? null : level.getCoefficient();
        }
    }

    public static DnaInfoResponse from(DnaInfo dna, AreaScores baseline) {
        return new DnaInfoResponse(
                dna.getCompletedAt(),
                dna.getSleepType(),
                new SleepIssues(
                        dna.isSleepDaytimeDrowsy(),
                        dna.isSleepOnsetDelayed(),
                        dna.isSleepNightAwakening(),
                        dna.isSleepUnrefreshed()),
                new SensitivityLevels(
                        dna.getSugarSensitivity(), dna.getCaffeineSensitivity(), dna.getStressSensitivity()),
                dna.getExerciseLevel(),
                new WorkStyle(dna.isShiftWorker(), dna.isFrequentTraveler()),
                dna.getDrinkFrequency(),
                dna.getSmokingStatus(),
                dna.getLifeRhythm(),
                dna.getSocialContactLevel(),
                // 문항 순서가 곧 의미다 — WHO-5 는 5문항 합계로만 환산되므로 개별 이름이 없다
                Arrays.asList(
                        dna.getWho5Q1(), dna.getWho5Q2(), dna.getWho5Q3(), dna.getWho5Q4(), dna.getWho5Q5()),
                AreaScoreResponse.from(baseline),
                SensitivityCoefficients.of(dna));
    }
}
