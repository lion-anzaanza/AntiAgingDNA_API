package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.service.scoring.Area;
import cloud.anzaanza.antiagingdna.service.scoring.AreaScores;
import cloud.anzaanza.antiagingdna.service.scoring.Grade;
import cloud.anzaanza.antiagingdna.service.scoring.GradeCalculator;
import java.math.BigDecimal;

/**
 * 영역별 0~100 점수. {@code null} 은 <b>0점이 아니라 근거 없음</b>이다 — 화면에서 0으로
 * 그리면 기록하지 않은 영역이 최악으로 보인다. {@link #grades} 도 같은 규칙을 따른다
 * (점수가 없으면 등급도 없다).
 */
public record AreaScoreResponse(
        BigDecimal physical,
        BigDecimal mental,
        BigDecimal emotion,
        BigDecimal social,
        BigDecimal environment,
        Grades grades) {

    /** {@link cloud.anzaanza.antiagingdna.config.ScoringProperties.GradeThresholds} 기준 등급 5종 */
    public record Grades(Grade physical, Grade mental, Grade emotion, Grade social, Grade environment) {}

    public static AreaScoreResponse from(AreaScores scores, ScoringProperties.GradeThresholds thresholds) {
        BigDecimal physical = AreaScores.toColumn(scores.get(Area.PHYSICAL));
        BigDecimal mental = AreaScores.toColumn(scores.get(Area.MENTAL));
        BigDecimal emotion = AreaScores.toColumn(scores.get(Area.EMOTION));
        BigDecimal social = AreaScores.toColumn(scores.get(Area.SOCIAL));
        BigDecimal environment = AreaScores.toColumn(scores.get(Area.ENVIRONMENT));
        return new AreaScoreResponse(
                physical,
                mental,
                emotion,
                social,
                environment,
                gradesOf(physical, mental, emotion, social, environment, thresholds));
    }

    /** {@code daily_score} 에 저장된 <b>당일</b> 영역 점수 (baseline 결합 전) */
    public static AreaScoreResponse ofDaily(DailyScore score, ScoringProperties.GradeThresholds thresholds) {
        BigDecimal physical = score.getPhysicalScore();
        BigDecimal mental = score.getMentalScore();
        BigDecimal emotion = score.getEmotionScore();
        BigDecimal social = score.getSocialScore();
        BigDecimal environment = score.getEnvironmentScore();
        return new AreaScoreResponse(
                physical,
                mental,
                emotion,
                social,
                environment,
                gradesOf(physical, mental, emotion, social, environment, thresholds));
    }

    private static Grades gradesOf(
            BigDecimal physical,
            BigDecimal mental,
            BigDecimal emotion,
            BigDecimal social,
            BigDecimal environment,
            ScoringProperties.GradeThresholds thresholds) {
        return new Grades(
                GradeCalculator.of(physical, thresholds),
                GradeCalculator.of(mental, thresholds),
                GradeCalculator.of(emotion, thresholds),
                GradeCalculator.of(social, thresholds),
                GradeCalculator.of(environment, thresholds));
    }
}
