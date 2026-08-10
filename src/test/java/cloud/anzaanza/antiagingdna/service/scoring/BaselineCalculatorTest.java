package cloud.anzaanza.antiagingdna.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContactLevel;
import org.junit.jupiter.api.Test;

/** 기획 [종합점수 산출 수식] §A-3 · §A-4 워크드 예시 대조. */
class BaselineCalculatorTest {

    /**
     * 기획 §A-4 답변: 수면질 1체크(75) · 운동 150~300(100) · 음주 월1↓(90) · 흡연 비흡연(100)
     * · 생활리듬 대체로(75) · 사회 주3~4(85) · 기분·활력 좋음
     */
    private static DnaInfo.DnaInfoBuilder workedExample() {
        return DnaInfo.builder()
                .sleepType(SleepType.MORNING)
                .sleepDaytimeDrowsy(true) // 체크 1개 → 100 − 25 = 75
                .sleepOnsetDelayed(false)
                .sleepNightAwakening(false)
                .sleepUnrefreshed(false)
                .sugarSensitivity(SensitivityLevel.MODERATE)
                .caffeineSensitivity(SensitivityLevel.MODERATE)
                .stressSensitivity(SensitivityLevel.MODERATE)
                .exerciseLevel(ExerciseLevel.FROM_150_TO_300) // 100
                .shiftWorker(false)
                .frequentTraveler(false)
                .drinkFrequency(DrinkFrequency.MONTHLY_OR_LESS) // 90
                .smokingStatus(SmokingStatus.NEVER) // 100
                .lifeRhythm(LifeRhythm.MOSTLY_REGULAR) // 75
                .socialContactLevel(SocialContactLevel.THREE_TO_FOUR_PER_WEEK); // 85
    }

    @Test
    void 신체_baseline_은_수면질_운동_음주_흡연의_평균이다() {
        AreaScores baseline = BaselineCalculator.of(workedExample().build());

        // 기획 §A-4: (75 + 100 + 90 + 100) / 4 = 91.3
        assertThat(baseline.get(Area.PHYSICAL)).isCloseTo(91.25, within(0.01));
    }

    @Test
    void 사회_baseline_은_사회교류_단일_소스다() {
        assertThat(BaselineCalculator.of(workedExample().build()).get(Area.SOCIAL)).isEqualTo(85.0);
    }

    @Test
    void 정신과_감정_baseline_은_WHO5_를_포함한다() {
        // WHO-5 합 19 → ×4 = 76
        DnaInfo dna = workedExample().who5Q1(4).who5Q2(4).who5Q3(4).who5Q4(4).who5Q5(3).build();

        AreaScores baseline = BaselineCalculator.of(dna);

        // 정신 = avg(수면질 75, 생활리듬 75, 사회교류 85, 웰빙 76)
        assertThat(baseline.get(Area.MENTAL)).isCloseTo(77.75, within(0.01));
        // 감정 = avg(생활리듬 75, 웰빙 76)
        assertThat(baseline.get(Area.EMOTION)).isCloseTo(75.5, within(0.01));
    }

    /**
     * 목업 {@code 02_INITIAL_DIAGNOSIS} 에 Q11·Q12 가 없어 실제로 자주 나오는 상태다.
     * 빈 문항을 0 으로 채우면 답하지 않은 것을 최악으로 채점하는 셈이 된다.
     */
    @Test
    void Phase2_문항이_비면_그_소스만_빠지고_나머지로_평균낸다() {
        DnaInfo dna = workedExample().socialContactLevel(null).build(); // WHO-5 도 전부 null

        AreaScores baseline = BaselineCalculator.of(dna);

        assertThat(baseline.get(Area.PHYSICAL)).isCloseTo(91.25, within(0.01)); // 영향 없음
        assertThat(baseline.get(Area.MENTAL)).isCloseTo(75.0, within(0.01)); // avg(75, 75)
        assertThat(baseline.get(Area.EMOTION)).isEqualTo(75.0); // 생활리듬 하나
        assertThat(baseline.get(Area.SOCIAL)).describedAs("소스가 없으면 0 이 아니라 결측").isNull();
    }

    /**
     * 기획 §A-3 은 {@code baseline_환경 = 50} 이지만 온보딩에 환경 입력이 없다. 모든 사용자에게
     * 같은 상수를 붙여 있는 정보인 척하지 않는다 (Phase 1 가중치도 0).
     */
    @Test
    void 환경_baseline_은_산출하지_않는다() {
        assertThat(BaselineCalculator.of(workedExample().build()).get(Area.ENVIRONMENT)).isNull();
    }

    /**
     * 기획 §A-4 의 '기분·활력 75' 는 <b>WHO-5 5문항으로는 나올 수 없는 값</b>이다 —
     * 합(0~25)×4 는 4의 배수뿐이다. 예시가 축약 5점 척도(0/25/50/75/100)를 쓰던 시절의
     * 잔재이고, 문항 수는 이후 5문항으로 확정됐다(일지 §6). 기획자 확인이 필요한 지점.
     */
    @Test
    void WHO5_5문항으로는_75점이_나올_수_없다() {
        for (int sum = 0; sum <= 25; sum++) {
            assertThat(sum * 4).isNotEqualTo(75);
        }
    }
}
