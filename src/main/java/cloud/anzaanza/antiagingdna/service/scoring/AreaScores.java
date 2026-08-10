package cloud.anzaanza.antiagingdna.service.scoring;

import cloud.anzaanza.antiagingdna.config.ScoringProperties.Weights;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 영역별 0~100 점수. 값이 <b>없는</b> 영역(소스가 하나도 기록되지 않음)은 담지 않는다 —
 * 0점과 결측은 다르다 (기획 일지 §5 "선택 항목 미입력 = 0점 아님. 제외 후 재정규화").
 */
public final class AreaScores {

    private final Map<Area, Double> scores;

    private AreaScores(Map<Area, Double> scores) {
        this.scores = Collections.unmodifiableMap(scores);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Double get(Area area) {
        return scores.get(area);
    }

    public boolean isEmpty() {
        return scores.isEmpty();
    }

    /**
     * 영역 가중합 {@code Score = Σ W_c × C_c} (기획 §6).
     *
     * <p>값이 없는 영역은 분모에서도 뺀다. 기획 §6 은 5개 영역이 모두 있다고 보고 나눗셈이
     * 없지만, Phase 1 은 사회 영역의 소스(사람 만남 · 온보딩 Q11)가 둘 다 미구현이라 항상
     * 비어 있다. 그대로 더하면 가중치 0.167 만큼이 조용히 사라져 만점이 83점이 된다.
     * 결측을 빼고 재정규화하는 것은 기획이 항목 단위에서 이미 쓰는 규칙(§4)과 같다.
     *
     * @return 값이 있는 영역이 하나도 없으면 {@code null}
     */
    public Double weightedTotal(Weights weights) {
        double weightedSum = 0;
        double weightSum = 0;
        for (Map.Entry<Area, Double> entry : scores.entrySet()) {
            double weight = entry.getKey().weightIn(weights).doubleValue();
            weightedSum += weight * entry.getValue();
            weightSum += weight;
        }
        return weightSum == 0 ? null : weightedSum / weightSum;
    }

    public static final class Builder {

        private final Map<Area, Double> scores = new EnumMap<>(Area.class);

        /** {@code null} 은 그 영역에 기록된 소스가 없다는 뜻이므로 담지 않는다 */
        public Builder put(Area area, Double score) {
            if (score != null) {
                scores.put(area, score);
            }
            return this;
        }

        public AreaScores build() {
            return new AreaScores(new EnumMap<>(scores));
        }
    }

    @Override
    public String toString() {
        return scores.toString();
    }

    /** 소수 둘째 자리로 반올림 — {@code daily_score} 컬럼이 {@code decimal(5,2)} 다 */
    public static BigDecimal toColumn(Double score) {
        return score == null ? null : BigDecimal.valueOf(score).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
