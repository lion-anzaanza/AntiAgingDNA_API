package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.service.scoring.Area;
import cloud.anzaanza.antiagingdna.service.scoring.AreaScores;
import java.math.BigDecimal;

/**
 * 영역별 0~100 점수. {@code null} 은 <b>0점이 아니라 근거 없음</b>이다 — 화면에서 0으로
 * 그리면 기록하지 않은 영역이 최악으로 보인다.
 */
public record AreaScoreResponse(
        BigDecimal physical,
        BigDecimal mental,
        BigDecimal emotion,
        BigDecimal social,
        BigDecimal environment) {

    public static AreaScoreResponse from(AreaScores scores) {
        return new AreaScoreResponse(
                AreaScores.toColumn(scores.get(Area.PHYSICAL)),
                AreaScores.toColumn(scores.get(Area.MENTAL)),
                AreaScores.toColumn(scores.get(Area.EMOTION)),
                AreaScores.toColumn(scores.get(Area.SOCIAL)),
                AreaScores.toColumn(scores.get(Area.ENVIRONMENT)));
    }

    /** {@code daily_score} 에 저장된 <b>당일</b> 영역 점수 (baseline 결합 전) */
    public static AreaScoreResponse ofDaily(DailyScore score) {
        return new AreaScoreResponse(
                score.getPhysicalScore(),
                score.getMentalScore(),
                score.getEmotionScore(),
                score.getSocialScore(),
                score.getEnvironmentScore());
    }
}
