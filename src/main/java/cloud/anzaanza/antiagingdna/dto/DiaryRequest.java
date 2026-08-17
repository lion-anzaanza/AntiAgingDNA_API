package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.User;
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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 오늘의 일지 입력 — 목업 {@code 01_CREATE_DIARY}.
 *
 * <p><b>컨디션만 필수고 나머지는 전부 선택이다.</b> 미입력은 0점이 아니라 결측으로 처리되어
 * 영역 집계에서 제외 후 재정규화된다(기획 일지 §5). 마찰 예산 60초 안에 쓰게 하려면 부분 입력이
 * 정상 경로여야 한다.
 *
 * <p>목업에 없지만 기획에서 채점 항목으로 확정된 5개(카페인 잔 수·시각, 기분 회복 활동,
 * 사람 만남, 걸은 시간)도 받는다. 선택 필드라 목업대로만 보내는 클라이언트도 그대로 동작하고,
 * 화면이 추가되면 서버 변경 없이 채점에 반영된다.
 *
 * <p>목업의 <b>아침 식사·야식은 받지 않는다</b> — 기획 일지 §3 이 "명확한 표준·역치가 없어
 * 제외"로 삭제한 항목이다.
 */
public record DiaryRequest(

        // ── 결과변수 (기획 일지 §1① — 종합점수 합산에서 제외) ──────
        @NotNull @Min(1) @Max(5) Integer conditionLevel,

        // ── 수면 ─────────────────────────────────────────────────
        // 날짜 없이 시각만 받는다 — 기상이 취침보다 이르거나 같으면 자정을 넘긴 것으로 본다
        // (ItemScores.sleepMinutes). 일지가 하루 1건이라 그 외의 해석이 없다.
        @Schema(type = "string", pattern = "HH:mm(:ss)?", example = "23:30") LocalTime sleepStartedAt,
        @Schema(type = "string", pattern = "HH:mm(:ss)?", example = "07:00") LocalTime sleepEndedAt,
        SleepLatency sleepLatency,
        @Min(1) @Max(5) Integer sleepSatisfaction,

        // ── 식·수분 ──────────────────────────────────────────────
        SugarIntake sugarIntake,
        CaffeineCups caffeineCups,
        CaffeineLastTime caffeineLastTime,
        WaterIntake waterIntake,

        // ── 활동 ─────────────────────────────────────────────────
        Boolean exercised,
        ExerciseDuration exerciseDuration,
        ExerciseType exerciseType,
        SittingHours sittingHours,

        // 0~10 NRS. 목업의 0~100 슬라이더가 아니다 (PLANNING_OPEN_ITEMS.md A-6, 2026-08-17 확정)
        @Min(0) @Max(10) Integer stressLevel,
        MoodRecovery moodRecovery,

        // ── 사회 ─────────────────────────────────────────────────
        SocialContact socialContact,

        // ── 참고 항목 (비채점) ───────────────────────────────────
        @Min(0) @Max(5) Integer mealCount,
        WalkDuration walkDuration,
        ScreenTime screenTime,

        // ── 날씨 (자동 기록, FE backend-backlog #12) ──────────────
        // 둘 다 있어야 조회한다 — 하나만 오면 위경도 자체가 불완전하니 결측으로 둔다
        // (DiaryService.save). 지명(locationLabel)은 서버가 좌표로 지어내지 않는다 —
        // 클라이언트가 기기 위치 서비스로 얻은 표기를 그대로 보낸다.
        @DecimalMin("-90") @DecimalMax("90") @Schema(example = "37.5665") Double lat,
        @DecimalMin("-180") @DecimalMax("180") @Schema(example = "126.9780") Double lon,
        @Schema(example = "서울") @Size(max = 64) String weatherLocationLabel) {

    /**
     * 운동을 "안 함"으로 보내면서 시간·종류가 함께 오면 그 둘을 버린다.
     *
     * <p>화면 토글을 껐을 때 이전 선택이 남아 함께 전송되는 상황이 정상적으로 발생한다.
     * 채점은 어차피 "안 함"을 0점으로 처리하므로(기획 §8), 모순된 값을 DB 에 남기지 않는다.
     */
    public DiaryRequest {
        if (Boolean.FALSE.equals(exercised)) {
            exerciseDuration = null;
            exerciseType = null;
        }
    }

    public Diary toEntity(User author, LocalDate logDate) {
        return Diary.builder()
                .author(author)
                .logDate(logDate)
                .conditionLevel(conditionLevel)
                .sleepStartedAt(sleepStartedAt)
                .sleepEndedAt(sleepEndedAt)
                .sleepLatency(sleepLatency)
                .sleepSatisfaction(sleepSatisfaction)
                .sugarIntake(sugarIntake)
                .caffeineCups(caffeineCups)
                .caffeineLastTime(caffeineLastTime)
                .waterIntake(waterIntake)
                .exercised(exercised)
                .exerciseDuration(exerciseDuration)
                .exerciseType(exerciseType)
                .sittingHours(sittingHours)
                .stressLevel(stressLevel)
                .moodRecovery(moodRecovery)
                .socialContact(socialContact)
                .mealCount(mealCount)
                .walkDuration(walkDuration)
                .screenTime(screenTime)
                .build();
    }
}
