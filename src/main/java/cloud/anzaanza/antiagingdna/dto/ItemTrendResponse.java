package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import cloud.anzaanza.antiagingdna.service.scoring.Grade;
import cloud.anzaanza.antiagingdna.service.scoring.GradeCalculator;
import cloud.anzaanza.antiagingdna.service.scoring.ItemScores;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 홈 "나의 LifeDNA 정보" 주간 추이 카드(수면·수분) 원자값 — FE backend-backlog.md #11.
 * 스트레스는 홈 지표 카드 등급 뱃지용(#10) — {@code stressScore} 는 채점 파이프라인과 같은
 * 웰빙 방향({@link ItemScores#stress}, 높을수록 좋음)이라 스트레스가 높으면 {@code DANGER} 다.
 * 등급 경계는 #22 결정대로 종합·영역·항목 공통.
 * 문장은 내려주지 않는다 — 서버는 원자값만 준다는 결정({@code PLANNING_OPEN_ITEMS.md} B-5)에
 * 따라, 막대·진행바를 그릴 숫자와 등급까지만 제공한다.
 *
 * <p>{@link Grade} 와 마찬가지로 표시 전용이라 저장하지 않고 매 요청마다 계산한다 — 경계값이
 * 바뀌어도 {@code scoring.version} 을 올릴 필요가 없다.
 */
public record ItemTrendResponse(
        LocalDate date,
        Long sleepMinutes,
        Double sleepScore,
        Grade sleepGrade,
        WaterIntake waterIntake,
        Double waterScore,
        Grade waterGrade,
        Integer stressLevel,
        Double stressScore,
        Grade stressGrade) {

    public static ItemTrendResponse from(Diary diary, ScoringProperties.GradeThresholds thresholds) {
        Double sleepScore = ItemScores.sleepDuration(diary.getSleepStartedAt(), diary.getSleepEndedAt());
        WaterIntake waterIntake = diary.getWaterIntake();
        Double waterScore = ItemScores.anchor(waterIntake);
        Integer stressLevel = diary.getStressLevel();
        Double stressScore = ItemScores.stress(stressLevel);

        return new ItemTrendResponse(
                diary.getLogDate(),
                ItemScores.sleepMinutes(diary.getSleepStartedAt(), diary.getSleepEndedAt()),
                sleepScore,
                GradeCalculator.of(toBigDecimal(sleepScore), thresholds),
                waterIntake,
                waterScore,
                GradeCalculator.of(toBigDecimal(waterScore), thresholds),
                stressLevel,
                stressScore,
                GradeCalculator.of(toBigDecimal(stressScore), thresholds));
    }

    private static BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
