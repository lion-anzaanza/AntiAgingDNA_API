package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.service.scoring.Grade;
import cloud.anzaanza.antiagingdna.service.scoring.GradeCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 하루치 종합점수.
 *
 * @param areas 그날 <b>일지만으로</b> 산출한 영역 점수. baseline 결합 전이라 일지가 없으면 전부 비어 있다
 * @param dailyTotal 당일 가중합 — 기획 §7 의 "오브(당일값)". 일지가 없는 날은 {@code null}
 * @param displayTotal 화면에 표시하는 종합점수 — baseline 과 결합한 "추세". 항상 존재한다
 * @param grade {@code displayTotal} 의 3단계 등급(좋음/주의/위험) — 2026-08-17 결정
 *     (FE backend-backlog.md #22). {@code displayTotal} 이 항상 존재하므로 이 필드도 항상 존재한다
 * @param scoringVersion 이 값을 산출한 파라미터 버전. 재보정 전후 점수를 섞어 보지 않기 위한 축이다
 */
public record DailyScoreResponse(
        LocalDate date,
        AreaScoreResponse areas,
        BigDecimal dailyTotal,
        BigDecimal displayTotal,
        Grade grade,
        String scoringVersion) {

    public static DailyScoreResponse from(DailyScore score, ScoringProperties.GradeThresholds thresholds) {
        return new DailyScoreResponse(
                score.getScoreDate(),
                AreaScoreResponse.ofDaily(score, thresholds),
                score.getDailyTotal(),
                score.getDisplayTotal(),
                GradeCalculator.of(score.getDisplayTotal(), thresholds),
                score.getScoringVersion());
    }
}
