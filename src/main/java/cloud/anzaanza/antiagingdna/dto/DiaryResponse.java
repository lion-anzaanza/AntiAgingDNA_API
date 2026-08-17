package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineCups;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineLastTime;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseDuration;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseType;
import cloud.anzaanza.antiagingdna.entity.enums.MoodRecovery;
import cloud.anzaanza.antiagingdna.entity.enums.ScreenTime;
import cloud.anzaanza.antiagingdna.entity.enums.SittingHours;
import cloud.anzaanza.antiagingdna.entity.enums.SleepLatency;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContact;
import cloud.anzaanza.antiagingdna.entity.enums.SugarIntake;
import cloud.anzaanza.antiagingdna.entity.enums.WalkDuration;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import cloud.anzaanza.antiagingdna.service.scoring.ItemScores;
import cloud.anzaanza.antiagingdna.service.weather.WeatherCondition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 저장된 일지. {@code null} 은 <b>미입력</b>이다 — 0 이나 "안 함"과 구분해서 그려야 한다.
 *
 * @param sleepMinutes 취침·기상에서 파생한 수면 시간(분). 저장하지 않고 조회 시 계산한다
 *     (기획 일지 §2 "Ⓘ 시각 입력 → 자동 계산")
 */
public record DiaryResponse(
        String id,
        LocalDate logDate,
        Integer conditionLevel,
        LocalTime sleepStartedAt,
        LocalTime sleepEndedAt,
        Long sleepMinutes,
        SleepLatency sleepLatency,
        Integer sleepSatisfaction,
        SugarIntake sugarIntake,
        CaffeineCups caffeineCups,
        CaffeineLastTime caffeineLastTime,
        WaterIntake waterIntake,
        Boolean exercised,
        ExerciseDuration exerciseDuration,
        ExerciseType exerciseType,
        SittingHours sittingHours,
        Integer stressLevel,
        MoodRecovery moodRecovery,
        SocialContact socialContact,
        Integer mealCount,
        WalkDuration walkDuration,
        ScreenTime screenTime,
        BigDecimal weatherTemperature,
        Integer weatherHumidity,
        WeatherCondition weatherCondition,
        String weatherLocationLabel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DiaryResponse from(Diary diary) {
        return new DiaryResponse(
                diary.getId(),
                diary.getLogDate(),
                diary.getConditionLevel(),
                diary.getSleepStartedAt(),
                diary.getSleepEndedAt(),
                ItemScores.sleepMinutes(diary.getSleepStartedAt(), diary.getSleepEndedAt()),
                diary.getSleepLatency(),
                diary.getSleepSatisfaction(),
                diary.getSugarIntake(),
                diary.getCaffeineCups(),
                diary.getCaffeineLastTime(),
                diary.getWaterIntake(),
                diary.getExercised(),
                diary.getExerciseDuration(),
                diary.getExerciseType(),
                diary.getSittingHours(),
                diary.getStressLevel(),
                diary.getMoodRecovery(),
                diary.getSocialContact(),
                diary.getMealCount(),
                diary.getWalkDuration(),
                diary.getScreenTime(),
                diary.getWeatherTemperature(),
                diary.getWeatherHumidity(),
                diary.getWeatherCondition(),
                diary.getWeatherLocationLabel(),
                diary.getCreatedAt(),
                diary.getUpdatedAt());
    }
}
