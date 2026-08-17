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

/** 홈 주간 추이 카드(수면·수분) 원자값 — FE backend-backlog.md #11. */
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
    void 미입력_항목은_점수와_등급도_결측이다() {
        Diary diary = Diary.builder().logDate(LocalDate.of(2026, 8, 10)).build();

        ItemTrendResponse response = ItemTrendResponse.from(diary, THRESHOLDS);

        assertThat(response.sleepMinutes()).isNull();
        assertThat(response.sleepScore()).isNull();
        assertThat(response.sleepGrade()).isNull();
        assertThat(response.waterIntake()).isNull();
        assertThat(response.waterScore()).isNull();
        assertThat(response.waterGrade()).isNull();
    }
}
