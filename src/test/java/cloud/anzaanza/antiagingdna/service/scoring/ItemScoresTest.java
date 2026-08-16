package cloud.anzaanza.antiagingdna.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 기획 [종합점수 산출 수식] §8 · §A-1 의 정규화 기준표를 그대로 대조한다. */
class ItemScoresTest {

    // ── 수면시간 (U자 · 이상 7~9h · 밖 1h당 15점) ─────────────────

    @ParameterizedTest(name = "{0}~{1} → {2}점")
    @CsvSource({
        // 기획 §8 "8h=100 · 6h/10h=85 · 5h=70"
        "23:00, 07:00, 100", // 8h — 이상구간 안
        "23:00, 05:00, 85", // 6h — 1h 미달
        "23:00, 09:00, 85", // 10h — 1h 초과
        "23:00, 04:00, 70", // 5h — 2h 미달
        "23:00, 06:10, 100", // 7h10m — 기획 §B-3 워크드 예시
        "22:00, 05:00, 100", // 7h — 경계 하한
        "22:00, 07:00, 100", // 9h — 경계 상한
        "03:00, 09:00, 85" // 자정을 넘기지 않는 수면도 같은 식
    })
    void 수면시간은_이상구간_7에서_9시간_밖으로_1시간당_15점씩_깎인다(String bedtime, String wakeTime, double expected) {
        assertThat(ItemScores.sleepDuration(LocalTime.parse(bedtime), LocalTime.parse(wakeTime)))
                .isEqualTo(expected);
    }

    @Test
    void 기상이_취침보다_이르면_자정을_넘긴_것으로_본다() {
        // 23:00 → 07:00 은 8시간이지, 16시간 전(음수)이 아니다
        assertThat(ItemScores.sleepDuration(LocalTime.of(23, 0), LocalTime.of(7, 0))).isEqualTo(100.0);
    }

    @Test
    void 수면시간이_지나치게_벗어나도_0_아래로_내려가지_않는다() {
        // 10분 수면 → 이상구간에서 6.83h 밖 → 100 − 102.5 → 0 으로 clamp
        assertThat(ItemScores.sleepDuration(LocalTime.of(23, 0), LocalTime.of(23, 10))).isEqualTo(0.0);
        // 1시간 수면은 아직 0 이 아니다 (d=6 → 10점) — clamp 가 과하게 걸리지 않는지도 본다
        assertThat(ItemScores.sleepDuration(LocalTime.of(4, 0), LocalTime.of(5, 0))).isEqualTo(10.0);
    }

    @Test
    void 취침이나_기상_하나라도_없으면_결측이다() {
        assertThat(ItemScores.sleepDuration(null, LocalTime.of(7, 0))).isNull();
        assertThat(ItemScores.sleepDuration(LocalTime.of(23, 0), null)).isNull();
    }

    // ── 나머지 정규화 ────────────────────────────────────────────

    @ParameterizedTest(name = "{0}점 척도 → {1}")
    @CsvSource({"1, 0", "2, 25", "3, 50", "4, 75", "5, 100"})
    void 오점_척도는_등간격이다(int value, double expected) {
        assertThat(ItemScores.fivePointScale(value)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "스트레스 {0} → {1}")
    @CsvSource({"0, 100.0", "1, 90.0", "6, 40.0", "10, 0.0"})
    void 스트레스는_0에서_10을_역채점한다(int level, double expected) {
        assertThat(ItemScores.stress(level)).isCloseTo(expected, within(0.01));
    }

    @ParameterizedTest(name = "체크 {0}개 → {1}")
    @CsvSource({"0, 100", "1, 75", "2, 50", "3, 25", "4, 0"})
    void 수면질_자가진단은_체크_하나당_25점씩_깎인다(int checked, double expected) {
        boolean[] checks = new boolean[4];
        for (int i = 0; i < checked; i++) {
            checks[i] = true;
        }
        assertThat(ItemScores.sleepSelfCheck(checks)).isEqualTo(expected);
    }

    @Test
    void WHO5_는_5문항_합계에_4를_곱한다() {
        assertThat(ItemScores.who5(5, 5, 5, 5, 5)).isEqualTo(100.0);
        assertThat(ItemScores.who5(0, 0, 0, 0, 0)).isEqualTo(0.0);
        assertThat(ItemScores.who5(3, 4, 4, 4, 4)).isEqualTo(76.0);
    }

    /** WHO-5 는 5문항 합계로만 타당도가 확보된 척도다. 일부만 더하면 다른 지표가 된다 */
    @Test
    void WHO5_는_한_문항이라도_비면_결측이다() {
        assertThat(ItemScores.who5(5, 5, 5, 5, null)).isNull();
    }

    // ── 민감도 계수 ──────────────────────────────────────────────

    /** 기획 §B-3: 패스트푸드 1~2회(55) → k=1.1 → 50.5 · 카페인(80) → 78 */
    @ParameterizedTest(name = "raw {0} × k({1}) → {2}")
    @CsvSource({
        "55, MODERATE, 50.5",
        "80, MODERATE, 78.0",
        "44.44, MODERATE, 38.89",
        "55, NONE, 68.5",
        "55, HIGH, 41.5"
    })
    void 민감도_계수는_감점분에만_곱한다(double raw, SensitivityLevel level, double expected) {
        assertThat(ItemScores.applySensitivity(raw, level)).isCloseTo(expected, within(0.01));
    }

    /** 민감한 사람이 잘 지킨 날을 깎지 않는다 — 감점분이 0 이면 계수를 곱해도 0 이다 */
    @Test
    void 만점은_민감도와_무관하게_만점이다() {
        for (SensitivityLevel level : SensitivityLevel.values()) {
            assertThat(ItemScores.applySensitivity(100.0, level)).isEqualTo(100.0);
        }
    }

    @Test
    void 민감도가_커도_0_아래로_내려가지_않는다() {
        assertThat(ItemScores.applySensitivity(0.0, SensitivityLevel.HIGH)).isEqualTo(0.0);
    }

    // ── 결측 처리 ────────────────────────────────────────────────

    /** 기획 일지 §5 "선택 항목 미입력 = 0점 아님. 제외 후 재정규화" */
    @Test
    void 평균은_결측을_0으로_세지_않고_빼고_나눈다() {
        assertThat(ItemScores.mean(100.0, null, 50.0)).isEqualTo(75.0);
        assertThat(ItemScores.mean(100.0, 0.0, 50.0)).isCloseTo(50.0, within(0.01));
    }

    @Test
    void 전부_결측이면_평균도_결측이다() {
        assertThat(ItemScores.mean(null, null)).isNull();
    }
}
