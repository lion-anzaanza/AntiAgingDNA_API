package cloud.anzaanza.antiagingdna.service.scoring;

import cloud.anzaanza.antiagingdna.config.ScoringProperties.Alpha;

/**
 * baseline ↔ 일지 결합 (기획 [종합점수 산출 수식] §5 · §C).
 *
 * <pre>
 * C_c(t) = (1 − α) × baseline_c + α × mean7(일지_c)
 * α(n)   = min(n / (n + shrinkage), cap)      n = 그 영역의 기록 일수
 * </pre>
 *
 * <p>경험적 베이즈 축소(empirical-Bayes shrinkage)다. 기록이 적을수록 온보딩 답변 쪽으로
 * 끌어당기고, 쌓일수록 실제 행동 쪽으로 넘긴다 — 가입 0일 α=0, 7일 0.5, 30일 0.81.
 * 기록 3일치로 사람을 단정하지 않으면서도 별도 규칙 없이 자동으로 전환된다.
 *
 * <p>α 가 영역마다 따로인 이유 — 수면은 매일 적고 사람 만남은 가끔 적는 식으로 영역별 기록
 * 밀도가 다르다. 한 영역의 기록량으로 다른 영역의 신뢰도를 대신 판단할 수 없다.
 */
public final class ScoreCombiner {

    private ScoreCombiner() {}

    /**
     * @param recordedDays 그 영역에 값이 기록된 날 수 (n)
     */
    public static double alpha(long recordedDays, Alpha config) {
        double raw = (double) recordedDays / (recordedDays + config.shrinkage());
        return Math.min(raw, config.cap().doubleValue());
    }

    /**
     * @param baseline 온보딩 baseline. 해당 영역 문항이 비어 있으면 {@code null}
     * @param recentMean 최근 {@code movingAverageDays} 일의 일지 영역 점수 평균.
     *     그 창에 기록이 없으면 {@code null}
     * @return 둘 다 없으면 {@code null} — 그 영역은 아직 아무 근거가 없다는 뜻이다
     */
    public static Double combine(Double baseline, Double recentMean, long recordedDays, Alpha config) {
        if (recentMean == null) {
            return baseline;
        }
        if (baseline == null) {
            // 온보딩 문항이 비어 있는 영역(Phase 2 미구현 문항)은 일지만으로 간다.
            // baseline 을 50 같은 중립값으로 채우면 없는 근거를 지어내는 것이다.
            return recentMean;
        }
        double weight = alpha(recordedDays, config);
        return (1 - weight) * baseline + weight * recentMean;
    }
}
