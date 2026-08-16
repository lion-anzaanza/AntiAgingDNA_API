package cloud.anzaanza.antiagingdna.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 오브 7색 경계 — 기존 등급(70/40) 경계를 하드 서브 경계로 삼아 세분화했는지 확인한다. */
class OrbStateCalculatorTest {

    private static final ScoringProperties.GradeThresholds THRESHOLDS =
            new ScoringProperties.GradeThresholds(new BigDecimal("70"), new BigDecimal("40"));

    @ParameterizedTest(name = "{0}점 → {1}")
    @CsvSource({
        "0, DANGER_LOW",
        "19.99, DANGER_LOW",
        "20, DANGER_HIGH",
        "39.99, DANGER_HIGH",
        "40, WARN_LOW",
        "54.99, WARN_LOW",
        "55, WARN_HIGH",
        "69.99, WARN_HIGH",
        "70, GOOD_LOW",
        "79.99, GOOD_LOW",
        "80, GOOD_MID",
        "89.99, GOOD_MID",
        "90, GOOD_HIGH",
        "100, GOOD_HIGH"
    })
    void 경계값을_정확히_나눈다(String score, OrbState expected) {
        assertThat(OrbStateCalculator.of(new BigDecimal(score), THRESHOLDS)).isEqualTo(expected);
    }

    /** 오브 색이 항상 등급 배지의 3단계 경계(70/40) 안에서만 세분화되는지 — 모순 방지의 핵심 */
    @ParameterizedTest(name = "{0}점은 등급 {1} 안의 하위 상태만 가진다")
    @CsvSource({"90, GOOD", "70, GOOD", "69.99, WARN", "40, WARN", "39.99, DANGER", "0, DANGER"})
    void 등급_경계와_절대_모순되지_않는다(String score, Grade grade) {
        OrbState orb = OrbStateCalculator.of(new BigDecimal(score), THRESHOLDS);
        assertThat(orb.name()).startsWith(grade.name());
    }

    @Test
    void 점수가_없으면_결측이다() {
        assertThat(OrbStateCalculator.of(null, THRESHOLDS)).isNull();
    }
}
