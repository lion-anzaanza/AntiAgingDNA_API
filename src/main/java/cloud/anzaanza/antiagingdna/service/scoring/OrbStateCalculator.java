package cloud.anzaanza.antiagingdna.service.scoring;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 점수 → {@link OrbState} 변환 (기획 근거는 {@link OrbState} 참고).
 *
 * <p>새 설정을 두지 않고 기존 {@code scoring.grade.*} 경계값에서 서브 경계를 계산한다 —
 * 위험/주의는 구간의 중점, 좋음은 구간을 3등분한 두 지점.
 */
public final class OrbStateCalculator {

    private OrbStateCalculator() {}

    /** @return {@code score} 가 {@code null}(근거 없음)이면 {@code null} — 0점으로 취급하지 않는다 */
    public static OrbState of(BigDecimal score, ScoringProperties.GradeThresholds thresholds) {
        if (score == null) {
            return null;
        }
        BigDecimal warnMin = thresholds.warnMin();
        BigDecimal goodMin = thresholds.goodMin();

        if (score.compareTo(goodMin) >= 0) {
            BigDecimal range = BigDecimal.valueOf(100).subtract(goodMin);
            BigDecimal third1 = goodMin.add(thirdPoint(range, 1));
            BigDecimal third2 = goodMin.add(thirdPoint(range, 2));
            if (score.compareTo(third2) >= 0) {
                return OrbState.GOOD_HIGH;
            }
            return score.compareTo(third1) >= 0 ? OrbState.GOOD_MID : OrbState.GOOD_LOW;
        }
        if (score.compareTo(warnMin) >= 0) {
            BigDecimal mid = warnMin.add(goodMin).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            return score.compareTo(mid) >= 0 ? OrbState.WARN_HIGH : OrbState.WARN_LOW;
        }
        BigDecimal mid = warnMin.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        return score.compareTo(mid) >= 0 ? OrbState.DANGER_HIGH : OrbState.DANGER_LOW;
    }

    /** {@code range} 를 3등분한 {@code numerator} 번째 지점 (1 → 1/3, 2 → 2/3) */
    private static BigDecimal thirdPoint(BigDecimal range, int numerator) {
        return range.multiply(BigDecimal.valueOf(numerator)).divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
    }
}
