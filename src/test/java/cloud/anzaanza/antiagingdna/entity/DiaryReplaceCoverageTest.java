package cloud.anzaanza.antiagingdna.entity;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * {@link Diary#replaceWith(Diary)} 가 <b>모든</b> 입력 필드를 옮기는지 리플렉션으로 확인한다.
 *
 * <p>손으로 19개를 복사하는 메서드라, 컬럼을 추가하고 여기 한 줄을 빠뜨리면 그 항목만 수정이
 * 반영되지 않는다. 조용히 옛 값이 남고 점수도 옛 값으로 계산되므로 눈에 띄지 않는다.
 * 필드 목록을 손으로 적어두면 같은 실수를 두 곳에서 하게 되므로 리플렉션으로 훑는다.
 */
class DiaryReplaceCoverageTest {

    /** 교체 대상이 아닌 필드 — 일지의 정체성과 감사 정보 */
    private static final List<String> PRESERVED = List.of("id", "author", "logDate");

    @Test
    void replaceWith_는_모든_입력_필드를_교체한다() throws Exception {
        Diary target = Diary.builder()
                .id("diary-1")
                .logDate(LocalDate.of(2026, 8, 10))
                .conditionLevel(1)
                .build();

        target.replaceWith(filled());

        SoftAssertions softly = new SoftAssertions();
        int checked = 0;
        for (Field field : Diary.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || PRESERVED.contains(field.getName())) {
                continue;
            }
            checked++;
            field.setAccessible(true);
            softly.assertThat(field.get(target))
                    .describedAs("Diary.replaceWith 가 '%s' 를 옮기지 않았다", field.getName())
                    .isNotNull();
        }
        softly.assertAll();

        assertThat(checked).describedAs("검사한 필드 수").isEqualTo(19);
    }

    @Test
    void replaceWith_는_id_와_작성자_날짜를_유지한다() {
        Diary target = Diary.builder()
                .id("diary-1")
                .logDate(LocalDate.of(2026, 8, 10))
                .conditionLevel(1)
                .build();

        target.replaceWith(filled());

        assertThat(target.getId()).isEqualTo("diary-1");
        assertThat(target.getLogDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    /** 부분 갱신이 아니라 전체 교체 — 이번에 안 보낸 항목은 지워져야 한다 */
    @Test
    void replaceWith_는_이번에_없는_값을_지운다() {
        Diary target = Diary.builder().id("diary-1").build();
        target.replaceWith(filled());

        target.replaceWith(Diary.builder().conditionLevel(3).build());

        assertThat(target.getConditionLevel()).isEqualTo(3);
        assertThat(target.getStressLevel()).describedAs("보내지 않은 항목은 결측으로 되돌아간다").isNull();
        assertThat(target.getWaterIntake()).isNull();
    }

    /** 모든 입력 필드가 채워진 일지 — 리플렉션 검사가 null 로 통과하지 않게 */
    private static Diary filled() {
        return Diary.builder()
                .conditionLevel(3)
                .sleepStartedAt(LocalTime.of(23, 0))
                .sleepEndedAt(LocalTime.of(7, 0))
                .sleepLatency(SleepLatency.WITHIN_15)
                .sleepSatisfaction(4)
                .sugarIntake(SugarIntake.ONE_TO_TWO)
                .caffeineCups(CaffeineCups.ONE_TO_TWO)
                .caffeineLastTime(CaffeineLastTime.AFTERNOON)
                .waterIntake(WaterIntake.SIX_TO_SEVEN)
                .exercised(true)
                .exerciseDuration(ExerciseDuration.ABOUT_30)
                .exerciseType(ExerciseType.AEROBIC)
                .sittingHours(SittingHours.FOUR_TO_EIGHT)
                .stressLevel(6)
                .moodRecovery(MoodRecovery.BRIEF)
                .socialContact(SocialContact.BRIEF)
                .mealCount(3)
                .walkDuration(WalkDuration.THIRTY_TO_60)
                .screenTime(ScreenTime.TWO_TO_FOUR)
                .build();
    }
}
