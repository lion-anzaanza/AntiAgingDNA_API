package cloud.anzaanza.antiagingdna.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineCups;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineLastTime;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseDuration;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SittingHours;
import cloud.anzaanza.antiagingdna.entity.enums.SleepLatency;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContact;
import cloud.anzaanza.antiagingdna.entity.enums.SugarIntake;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** 기획 [종합점수 산출 수식] §B-2 · §B-3 워크드 예시 대조. */
class DiaryScorerTest {

    /** 민감도는 전부 '보통'(k=1.1) — 기획 §B-3 예시와 동일 */
    private static final DnaInfo MODERATE_SENSITIVITY = DnaInfo.builder()
            .sugarSensitivity(SensitivityLevel.MODERATE)
            .caffeineSensitivity(SensitivityLevel.MODERATE)
            .stressSensitivity(SensitivityLevel.MODERATE)
            .build();

    /**
     * 기획 §B-3: 수면 7h10m · 잠들기 15분 · 수면만족 보통 · 패스트푸드 1~2회 · 카페인 1~2잔 오후
     * · 물 6~7잔 · 운동 30분 · 좌식 8~10h · 스트레스 6 · 사람만남 잠깐
     */
    private static Diary.DiaryBuilder workedExample() {
        return Diary.builder()
                .conditionLevel(3)
                .sleepStartedAt(LocalTime.of(23, 0))
                .sleepEndedAt(LocalTime.of(6, 10)) // 7h10m → 100
                .sleepLatency(SleepLatency.WITHIN_15) // 75
                .sleepSatisfaction(3) // 보통 → 50
                .sugarIntake(SugarIntake.ONE_TO_TWO) // 55 → k1.1 → 50.5
                .caffeineCups(CaffeineCups.ONE_TO_TWO) // 80
                .caffeineLastTime(CaffeineLastTime.AFTERNOON) // 80 → avg 80 → k1.1 → 78
                .waterIntake(WaterIntake.SIX_TO_SEVEN) // 85
                .exercised(true)
                .exerciseDuration(ExerciseDuration.ABOUT_30) // 55
                .sittingHours(SittingHours.EIGHT_TO_TEN) // 45
                .stressLevel(6) // 40.0 → k1.1 → 34.0 (스트레스 도메인 0~10 확정, 2026-08-17)
                .socialContact(SocialContact.BRIEF); // 60
    }

    @Test
    void 신체는_여덟_항목의_평균이다() {
        AreaScores scores = DiaryScorer.of(workedExample().build(), MODERATE_SENSITIVITY);

        // 기획 §B-3: (100+75+50+50.5+78+85+55+45)/8 = 538.5/8 = 67.3
        assertThat(scores.get(Area.PHYSICAL)).isCloseTo(67.3125, within(0.01));
    }

    @Test
    void 정신은_수면_카페인_스트레스_사람만남의_평균이다() {
        AreaScores scores = DiaryScorer.of(workedExample().build(), MODERATE_SENSITIVITY);

        // 기획 §B-3 예시(67.0)는 스트레스 1~10 도메인 기준. 0~10 확정(2026-08-17)으로
        // 스트레스 원점수가 44.44→40.0 이 되며 (100+75+50+78+34+60)/6 = 66.17 로 소폭 이동한다.
        assertThat(scores.get(Area.MENTAL)).isCloseTo(66.17, within(0.01));
    }

    @Test
    void 사회는_사람만남_단일_소스다() {
        assertThat(DiaryScorer.of(workedExample().build(), MODERATE_SENSITIVITY).get(Area.SOCIAL))
                .isEqualTo(60.0);
    }

    /**
     * 기획 §B-2 의 감정은 avg(스트레스, 기분 회복 활동)이지만 '기분 회복 활동'의 앵커가
     * §8 정규화 기준표에 없다(워크드 예시의 '잠깐=60' 하나뿐). 지어내지 않고 스트레스만 쓴다.
     */
    @Test
    void 감정은_당분간_스트레스_단일_소스다() {
        assertThat(DiaryScorer.of(workedExample().build(), MODERATE_SENSITIVITY).get(Area.EMOTION))
                .isCloseTo(34.0, within(0.01));
    }

    /**
     * 카페인은 잔 수와 시각을 <b>먼저 평균낸 뒤</b> 계수를 곱한다. 각각에 곱하면 카페인이
     * 다른 항목의 두 배 무게가 된다 — 신체 평균의 분모가 8 이 아니라 9 가 되어 값이 달라진다.
     */
    @Test
    void 카페인_두_컬럼은_한_항목으로_합쳐진_뒤_계수가_곱해진다() {
        Diary onlyCaffeine = Diary.builder()
                .conditionLevel(3)
                .caffeineCups(CaffeineCups.ONE_TO_TWO) // 80
                .caffeineLastTime(CaffeineLastTime.AFTERNOON) // 80
                .build();

        // 신체 영역에 카페인만 있으므로 그 값이 곧 영역 점수다
        assertThat(DiaryScorer.of(onlyCaffeine, MODERATE_SENSITIVITY).get(Area.PHYSICAL))
                .isCloseTo(78.0, within(0.01));
    }

    // ── 운동: "안 함" 은 결측이 아니라 0점 ────────────────────────

    @Test
    void 운동을_안_했으면_0점이지_결측이_아니다() {
        Diary noExercise = Diary.builder().conditionLevel(3).exercised(false).build();

        assertThat(DiaryScorer.of(noExercise, MODERATE_SENSITIVITY).get(Area.PHYSICAL)).isEqualTo(0.0);
    }

    @Test
    void 운동_여부를_아예_입력하지_않으면_결측이다() {
        Diary blank = Diary.builder().conditionLevel(3).build();

        assertThat(DiaryScorer.of(blank, MODERATE_SENSITIVITY).get(Area.PHYSICAL)).isNull();
    }

    // ── 민감도 개인화 ────────────────────────────────────────────

    /** 같은 행동이라도 민감도가 다르면 점수가 달라진다 — 이 서비스의 개인화 축이다 */
    @Test
    void 민감도가_높을수록_같은_일지가_더_깎인다() {
        Diary diary = workedExample().build();
        DnaInfo insensitive = DnaInfo.builder()
                .sugarSensitivity(SensitivityLevel.NONE)
                .caffeineSensitivity(SensitivityLevel.NONE)
                .stressSensitivity(SensitivityLevel.NONE)
                .build();
        DnaInfo sensitive = DnaInfo.builder()
                .sugarSensitivity(SensitivityLevel.HIGH)
                .caffeineSensitivity(SensitivityLevel.HIGH)
                .stressSensitivity(SensitivityLevel.HIGH)
                .build();

        assertThat(DiaryScorer.of(diary, sensitive).get(Area.PHYSICAL))
                .isLessThan(DiaryScorer.of(diary, insensitive).get(Area.PHYSICAL));
    }

    /** 컨디션은 점수가 예측해야 할 결과값이라 합산에 넣으면 순환논리가 된다 (기획 일지 §1①) */
    @Test
    void 컨디션은_어떤_영역에도_들어가지_않는다() {
        Diary worst = workedExample().conditionLevel(1).build();
        Diary best = workedExample().conditionLevel(5).build();

        AreaScores worstScores = DiaryScorer.of(worst, MODERATE_SENSITIVITY);
        AreaScores bestScores = DiaryScorer.of(best, MODERATE_SENSITIVITY);

        for (Area area : Area.values()) {
            assertThat(worstScores.get(area)).isEqualTo(bestScores.get(area));
        }
    }
}
