package cloud.anzaanza.antiagingdna.dto;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import cloud.anzaanza.antiagingdna.service.scoring.Grade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** 홈 지표 카드(수면·수분·스트레스) 원자값 — FE backend-backlog.md #11/#10. */
class ItemTrendResponseTest {

    private static final ScoringProperties.GradeThresholds THRESHOLDS =
            new ScoringProperties.GradeThresholds(new BigDecimal("70"), new BigDecimal("40"));

    @Test
    void 수면과_수분을_점수와_등급으로_함께_준다() {
        Diary diary = Diary.builder()
                .logDate(LocalDate.of(2026, 8, 10))
                .sleepStartedAt(LocalTime.of(23, 0))
                .sleepEndedAt(LocalTime.of(7, 0)) // 8h → 100 → GOOD
                .waterIntake(WaterIntake.THREE_TO_FIVE) // 60 → WARN
                .build();

        ItemTrendResponse response = ItemTrendResponse.from(diary, THRESHOLDS);

        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(response.sleepMinutes()).isEqualTo(480L);
        assertThat(response.sleepScore()).isEqualTo(100.0);
        assertThat(response.sleepGrade()).isEqualTo(Grade.GOOD);
        assertThat(response.waterIntake()).isEqualTo(WaterIntake.THREE_TO_FIVE);
        assertThat(response.waterScore()).isEqualTo(60.0);
        assertThat(response.waterGrade()).isEqualTo(Grade.WARN);
    }

    @Test
    void 스트레스는_웰빙_방향_점수와_항목_공통_경계로_등급을_준다() {
        assertThat(stressOf(3).stressScore()).isEqualTo(70.0);
        assertThat(stressOf(3).stressGrade()).isEqualTo(Grade.GOOD);
        assertThat(stressOf(4).stressGrade()).isEqualTo(Grade.WARN);
        assertThat(stressOf(6).stressGrade()).isEqualTo(Grade.WARN);
        assertThat(stressOf(7).stressGrade()).isEqualTo(Grade.DANGER);
        assertThat(stressOf(10).stressScore()).isEqualTo(0.0);
        assertThat(stressOf(7).stressLevel()).isEqualTo(7);
    }

    @Test
    void 미입력_항목은_점수와_등급도_결측이다() {
        Diary diary = Diary.builder().logDate(LocalDate.of(2026, 8, 10)).build();

        ItemTrendResponse response = ItemTrendResponse.from(diary, THRESHOLDS);

        assertThat(response.sleepMinutes()).isNull();
        assertThat(response.sleepScore()).isNull();
        assertThat(response.sleepGrade()).isNull();
        assertThat(response.waterIntake()).isNull();
        assertThat(response.waterScore()).isNull();
        assertThat(response.waterGrade()).isNull();
        assertThat(response.stressLevel()).isNull();
        assertThat(response.stressScore()).isNull();
        assertThat(response.stressGrade()).isNull();
    }

    private static ItemTrendResponse stressOf(int level) {
        Diary diary = Diary.builder().logDate(LocalDate.of(2026, 8, 10)).stressLevel(level).build();
        return ItemTrendResponse.from(diary, THRESHOLDS);
    }
}
