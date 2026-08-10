package cloud.anzaanza.antiagingdna.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cloud.anzaanza.antiagingdna.config.ScoringProperties.Alpha;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** baseline ↔ 일지 결합 (기획 [종합점수 산출 수식] §5). */
class ScoreCombinerTest {

    private static final Alpha ALPHA = new Alpha(7, new BigDecimal("0.95"));

    @ParameterizedTest(name = "n={0} → α={1}")
    @CsvSource({
        "0, 0.0", // 가입 당일 — 온보딩만
        "7, 0.5", // 기획 §5 "7일 0.5"
        "30, 0.81", // 기획 §5 "30일 0.81"
        "365, 0.95" // 상한에 걸린다
    })
    void 알파는_기록이_쌓일수록_일지_쪽으로_넘어간다(long recordedDays, double expected) {
        assertThat(ScoreCombiner.alpha(recordedDays, ALPHA)).isCloseTo(expected, within(0.01));
    }

    @Test
    void 알파는_상한을_넘지_않는다() {
        // 기록이 아무리 쌓여도 온보딩 답변의 지분을 완전히 없애지 않는다
        assertThat(ScoreCombiner.alpha(Long.MAX_VALUE / 2, ALPHA)).isEqualTo(0.95);
    }

    @Test
    void 가입_당일에는_baseline_이_그대로_표시된다() {
        assertThat(ScoreCombiner.combine(80.0, null, 0, ALPHA)).isEqualTo(80.0);
    }

    @Test
    void 칠일차에는_baseline_과_일지가_반반이다() {
        // 기획 §C 예시: α=0.5, baseline 91.25 · 일지 67.3 → 79.3
        assertThat(ScoreCombiner.combine(91.25, 67.3, 7, ALPHA)).isCloseTo(79.28, within(0.01));
    }

    /**
     * 온보딩 문항이 비어 있는 영역(Phase 2 미구현)은 일지만으로 간다. baseline 을 50 같은
     * 중립값으로 채우면 없는 근거를 지어내는 것이고, 실제 기록을 그쪽으로 끌어당긴다.
     */
    @Test
    void baseline_이_없으면_일지_평균을_그대로_쓴다() {
        assertThat(ScoreCombiner.combine(null, 67.3, 3, ALPHA)).isEqualTo(67.3);
    }

    @Test
    void 근거가_하나도_없으면_결측이다() {
        assertThat(ScoreCombiner.combine(null, null, 0, ALPHA)).isNull();
    }

    /** 기록이 쌓일수록 결합값이 일지 쪽으로 단조 이동해야 한다 */
    @Test
    void 기록이_쌓일수록_결합값이_일지_쪽으로_이동한다() {
        double previous = ScoreCombiner.combine(100.0, 0.0, 0, ALPHA);
        for (long days = 1; days <= 60; days++) {
            double current = ScoreCombiner.combine(100.0, 0.0, days, ALPHA);
            assertThat(current).isLessThanOrEqualTo(previous);
            previous = current;
        }
        // 60일차 α = 60/67 ≈ 0.896 — 아직 상한(0.95)에 닿지 않았다
        assertThat(previous).isCloseTo(10.45, within(0.01));
    }

    /** 상한 덕분에 baseline 의 지분이 완전히 0 이 되지는 않는다 */
    @Test
    void 기록이_아무리_쌓여도_baseline_지분_5퍼센트가_남는다() {
        assertThat(ScoreCombiner.combine(100.0, 0.0, 10_000, ALPHA)).isCloseTo(5.0, within(0.01));
    }
}
