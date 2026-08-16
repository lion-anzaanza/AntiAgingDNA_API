package cloud.anzaanza.antiagingdna.service.scoring;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import java.math.BigDecimal;

/** 점수 → {@link Grade} 변환 (기획 근거는 {@link Grade} 참고). */
public final class GradeCalculator {

    private GradeCalculator() {}

    /** @return {@code score} 가 {@code null}(근거 없음)이면 {@code null} — 0점으로 취급하지 않는다 */
    public static Grade of(BigDecimal score, ScoringProperties.GradeThresholds thresholds) {
        if (score == null) {
            return null;
        }
        if (score.compareTo(thresholds.goodMin()) >= 0) {
            return Grade.GOOD;
        }
        if (score.compareTo(thresholds.warnMin()) >= 0) {
            return Grade.WARN;
        }
        return Grade.DANGER;
    }
}
