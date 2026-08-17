package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.service.scoring.Grade;
import cloud.anzaanza.antiagingdna.service.scoring.GradeCalculator;
import cloud.anzaanza.antiagingdna.service.scoring.OrbState;
import cloud.anzaanza.antiagingdna.service.scoring.OrbStateCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 하루치 종합점수.
 *
 * @param areas 그날 <b>일지만으로</b> 산출한 영역 점수. baseline 결합 전이라 일지가 없으면 전부 비어 있다
 * @param dailyTotal 당일 가중합 — 기획 §7 의 "오브(당일값)". 일지가 없는 날은 {@code null}
 * @param displayTotal 화면에 표시하는 종합점수 — baseline 과 결합한 "추세". 항상 존재한다
 * @param grade {@code displayTotal} 의 3단계 등급 — **누적·추세 기준**이다. 홈 오브 카드처럼
 *     "이 사용자의 현재 수준"을 보여주는 자리에 쓴다. {@code displayTotal} 이 항상 존재하므로
 *     이 필드도 항상 존재한다
 * @param dailyGrade {@code dailyTotal} 의 3단계 등급 — **그날 하루만의 기준**이다(2026-08-17,
 *     FE backend-backlog.md #32). 캘린더 날짜 색·지난 기록 이모티콘처럼 "그날 기록이 어땠는지"를
 *     보여주는 자리엔 {@code grade} 대신 이걸 써야 한다 — {@code grade} 는 baseline 이 섞여
 *     있어서 최악의 날에도 GOOD 이 나올 수 있다. {@code dailyTotal} 처럼 일지가 없는 날은
 *     {@code null}
 * @param orbState {@code displayTotal} 의 홈 오브 색상 7단계 — 2026-08-17 결정
 *     (FE backend-backlog.md #25). {@code grade} 의 70/40 경계를 하드 서브 경계로 삼아 세분화한다
 * @param scoringVersion 이 값을 산출한 파라미터 버전. 재보정 전후 점수를 섞어 보지 않기 위한 축이다
 */
public record DailyScoreResponse(
        LocalDate date,
        AreaScoreResponse areas,
        BigDecimal dailyTotal,
        BigDecimal displayTotal,
        Grade grade,
        Grade dailyGrade,
        OrbState orbState,
        String scoringVersion) {

    public static DailyScoreResponse from(DailyScore score, ScoringProperties.GradeThresholds thresholds) {
        return new DailyScoreResponse(
                score.getScoreDate(),
                AreaScoreResponse.ofDaily(score, thresholds),
                score.getDailyTotal(),
                score.getDisplayTotal(),
                GradeCalculator.of(score.getDisplayTotal(), thresholds),
                GradeCalculator.of(score.getDailyTotal(), thresholds),
                OrbStateCalculator.of(score.getDisplayTotal(), thresholds),
                score.getScoringVersion());
    }
}
