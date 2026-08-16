package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.service.scoring.Area;
import cloud.anzaanza.antiagingdna.service.scoring.AreaScores;
import cloud.anzaanza.antiagingdna.service.scoring.Grade;
import cloud.anzaanza.antiagingdna.service.scoring.GradeCalculator;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

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
        Map<Area, BigDecimal> values = new EnumMap<>(Area.class);
        for (Area area : Area.values()) {
            values.put(area, AreaScores.toColumn(scores.get(area)));
        }
        return of(values, thresholds);
    }

    /** {@code daily_score} 에 저장된 <b>당일</b> 영역 점수 (baseline 결합 전) */
    public static AreaScoreResponse ofDaily(DailyScore score, ScoringProperties.GradeThresholds thresholds) {
        Map<Area, BigDecimal> values = new EnumMap<>(Area.class);
        values.put(Area.PHYSICAL, score.getPhysicalScore());
        values.put(Area.MENTAL, score.getMentalScore());
        values.put(Area.EMOTION, score.getEmotionScore());
        values.put(Area.SOCIAL, score.getSocialScore());
        values.put(Area.ENVIRONMENT, score.getEnvironmentScore());
        return of(values, thresholds);
    }

    /**
     * 영역-값 맵 하나로 점수 필드·등급을 함께 만든다. {@link Area} 키로 읽어서, 예전처럼
     * 5개를 순서로만 구분되는 익명 지역변수로 두 번(레코드 생성자·등급 계산) 나열하지
     * 않는다 — 영역이 늘거나 순서가 바뀌어도 어느 값이 어느 영역인지 실수로 어긋날 수 없다.
     */
    private static AreaScoreResponse of(
            Map<Area, BigDecimal> values, ScoringProperties.GradeThresholds thresholds) {
        return new AreaScoreResponse(
                values.get(Area.PHYSICAL),
                values.get(Area.MENTAL),
                values.get(Area.EMOTION),
                values.get(Area.SOCIAL),
                values.get(Area.ENVIRONMENT),
                new Grades(
                        GradeCalculator.of(values.get(Area.PHYSICAL), thresholds),
                        GradeCalculator.of(values.get(Area.MENTAL), thresholds),
                        GradeCalculator.of(values.get(Area.EMOTION), thresholds),
                        GradeCalculator.of(values.get(Area.SOCIAL), thresholds),
                        GradeCalculator.of(values.get(Area.ENVIRONMENT), thresholds)));
    }
}
