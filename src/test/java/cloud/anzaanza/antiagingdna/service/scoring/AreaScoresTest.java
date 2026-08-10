package cloud.anzaanza.antiagingdna.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cloud.anzaanza.antiagingdna.config.ScoringProperties.Weights;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 영역 가중합 (기획 [종합점수 산출 수식] §6). */
class AreaScoresTest {

    /** 기획 §6 원안 가중치 — 워크드 예시가 이 값으로 계산돼 있다 */
    private static final Weights PLAN_WEIGHTS = new Weights(
            new BigDecimal("0.30"),
            new BigDecimal("0.25"),
            new BigDecimal("0.20"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"));

    /** 운영 기본값 — 환경(상수라 변별력 없음)을 빼고 나머지를 재정규화 */
    private static final Weights PRODUCTION_WEIGHTS = new Weights(
            new BigDecimal("0.333"),
            new BigDecimal("0.278"),
            new BigDecimal("0.222"),
            new BigDecimal("0.167"),
            BigDecimal.ZERO);

    @Test
    void 기획_A4_워크드_예시_day0_은_80점이다() {
        AreaScores baseline = AreaScores.builder()
                .put(Area.PHYSICAL, 91.25)
                .put(Area.MENTAL, 77.5)
                .put(Area.EMOTION, 75.0)
                .put(Area.SOCIAL, 85.0)
                .put(Area.ENVIRONMENT, 50.0)
                .build();

        // 91.3×.30 + 77.5×.25 + 75×.20 + 85×.15 + 50×.10 = 79.6 → 80점
        assertThat(baseline.weightedTotal(PLAN_WEIGHTS)).isCloseTo(79.6, within(0.1));
    }

    @Test
    void 기획_B3_워크드_예시_당일점수는_61점이다() {
        AreaScores daily = AreaScores.builder()
                .put(Area.PHYSICAL, 67.3)
                .put(Area.MENTAL, 67.0)
                .put(Area.EMOTION, 49.5)
                .put(Area.SOCIAL, 60.0)
                .put(Area.ENVIRONMENT, 50.0)
                .build();

        // 67.3×.30 + 67.0×.25 + 49.5×.20 + 60×.15 + 50×.10 = 60.9 → 61점
        assertThat(daily.weightedTotal(PLAN_WEIGHTS)).isCloseTo(60.9, within(0.1));
    }

    /**
     * 기획 §6 은 5영역이 모두 있다고 보고 나눗셈이 없다. Phase 1 은 사회 영역 소스가 둘 다
     * 미구현이라 항상 비는데, 그대로 더하면 가중치 0.167 이 조용히 사라져 <b>만점이 83점</b>이
     * 된다. 결측을 빼고 재정규화하는 것은 기획이 항목 단위에서 이미 쓰는 규칙(§4)과 같다.
     */
    @Test
    void 값이_없는_영역은_분모에서도_빠진다() {
        AreaScores allHundred = AreaScores.builder()
                .put(Area.PHYSICAL, 100.0)
                .put(Area.MENTAL, 100.0)
                .put(Area.EMOTION, 100.0)
                .build();

        assertThat(allHundred.weightedTotal(PRODUCTION_WEIGHTS))
                .describedAs("사회가 비었다고 만점이 깎이면 안 된다")
                .isCloseTo(100.0, within(0.001));
    }

    @Test
    void 가중치가_0인_영역은_종합점수를_움직이지_않는다() {
        AreaScores withEnvironment = AreaScores.builder()
                .put(Area.PHYSICAL, 80.0)
                .put(Area.ENVIRONMENT, 0.0)
                .build();
        AreaScores withoutEnvironment = AreaScores.builder().put(Area.PHYSICAL, 80.0).build();

        assertThat(withEnvironment.weightedTotal(PRODUCTION_WEIGHTS))
                .isEqualTo(withoutEnvironment.weightedTotal(PRODUCTION_WEIGHTS));
    }

    @Test
    void 값이_하나도_없으면_종합점수도_없다() {
        assertThat(AreaScores.builder().build().weightedTotal(PRODUCTION_WEIGHTS)).isNull();
    }

    @Test
    void null_영역은_담기지_않는다() {
        AreaScores scores = AreaScores.builder().put(Area.SOCIAL, null).build();

        assertThat(scores.isEmpty()).isTrue();
        assertThat(scores.get(Area.SOCIAL)).isNull();
    }

    @Test
    void 컬럼_변환은_소수_둘째자리로_반올림한다() {
        assertThat(AreaScores.toColumn(67.3125)).isEqualByComparingTo("67.31");
        assertThat(AreaScores.toColumn(38.888888)).isEqualByComparingTo("38.89");
        assertThat(AreaScores.toColumn(null)).isNull();
    }
}
