package cloud.anzaanza.antiagingdna.config;

import cloud.anzaanza.antiagingdna.dto.DiagnosisRequest;
import cloud.anzaanza.antiagingdna.dto.DiaryRequest;
import cloud.anzaanza.antiagingdna.dto.SignUpRequest;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineCups;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineLastTime;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseDuration;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseType;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.MoodRecovery;
import cloud.anzaanza.antiagingdna.entity.enums.ScreenTime;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SittingHours;
import cloud.anzaanza.antiagingdna.entity.enums.SleepLatency;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContact;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContactLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SugarIntake;
import cloud.anzaanza.antiagingdna.entity.enums.WalkDuration;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
import cloud.anzaanza.antiagingdna.service.AuthService;
import cloud.anzaanza.antiagingdna.service.DiaryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 개발용 시드 계정 — FE backend-backlog.md #17, 2026-08-17 결정("운영 DB에 시드 계정 심기").
 *
 * <p>가입자가 아직 없는 단계라 운영 DB에 직접 심어도 실사용자 데이터와 섞일 위험이 없다는
 * 판단에 따른 것이다. 부팅마다 실행되지만 {@link #LOGIN_ID} 계정이 이미 있으면 그냥
 * 끝난다 — 매번 다시 심지 않는다.
 *
 * <p>손으로 SQL 을 심지 않고 {@link AuthService}/{@link DiaryService} 를 그대로 호출한다.
 * 비밀번호 해시·검증·day-0 채점 등 가입 경로의 규칙을 다시 만들지 않기 위해서다.
 *
 * <p>{@code app.seed-demo.enabled=false} 로 끌 수 있다 — 통합테스트가 이 값을 꺼서 매
 * 테스트마다 계정 15개 쓰기가 섞이는 걸 막는다({@code support/IntegrationTest} 참고).
 */
@Component
@ConditionalOnProperty(prefix = "app.seed-demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoAccountSeeder implements ApplicationRunner {

    private static final String LOGIN_ID = "demo";
    private static final String EMAIL = "demo@anzaanza.cloud";
    private static final String PASSWORD = "Demo1234";

    /** FE 가 캘린더·주간 그래프·연속 기록일을 확인할 수 있을 만큼의 길이 */
    private static final int SEED_DAYS = 14;

    private final UserRepository userRepository;
    private final AuthService authService;
    private final DiaryService diaryService;
    private final Clock clock;

    public DemoAccountSeeder(
            UserRepository userRepository, AuthService authService, DiaryService diaryService, Clock clock) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.diaryService = diaryService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByLoginId(LOGIN_ID)) {
            return;
        }
        User user = authService.signUp(signUpRequest());

        LocalDate today = LocalDate.now(clock);
        for (int daysAgo = SEED_DAYS - 1; daysAgo >= 0; daysAgo--) {
            diaryService.save(user.getId(), today.minusDays(daysAgo), diaryFor(daysAgo));
        }
    }

    private SignUpRequest signUpRequest() {
        Map<AgreementType, Boolean> agreements = new EnumMap<>(AgreementType.class);
        for (AgreementType type : AgreementType.values()) {
            agreements.put(type, true);
        }
        int birthYear = LocalDate.now(clock).getYear() - 25;
        return new SignUpRequest(LOGIN_ID, EMAIL, PASSWORD, "데모", birthYear, diagnosis(), agreements);
    }

    private static DiagnosisRequest diagnosis() {
        return new DiagnosisRequest(
                SleepType.NORMAL,
                false,
                false,
                false,
                false,
                SensitivityLevel.SLIGHT,
                SensitivityLevel.MODERATE,
                SensitivityLevel.SLIGHT,
                ExerciseLevel.FROM_150_TO_300,
                false,
                false,
                DrinkFrequency.MONTHLY_OR_LESS,
                SmokingStatus.NEVER,
                LifeRhythm.MOSTLY_REGULAR,
                SocialContactLevel.THREE_TO_FOUR_PER_WEEK,
                3,
                4,
                3,
                4,
                3);
    }

    /**
     * 하루치 일지를 지어낸다. 매일 같은 값이면 캘린더·주간 그래프가 평평한 직선으로만
     * 보여서 화면 확인에 쓸모가 없다 — {@code daysAgo} 로 몇 가지 값을 돌려가며 자연스러운
     * 굴곡을 만든다. 값 자체에 의미를 담진 않는다(진짜 사용자 패턴을 흉내내려는 것이 아니라
     * 화면에 변화가 보이게 하는 것이 목적이다).
     */
    private static DiaryRequest diaryFor(int daysAgo) {
        boolean exercised = daysAgo % 2 == 0;
        return new DiaryRequest(
                3 + (daysAgo % 3), // conditionLevel 3~5
                daysAgo % 2 == 0 ? LocalTime.of(23, 30) : LocalTime.of(0, 15),
                LocalTime.of(7, daysAgo % 2 == 0 ? 0 : 30),
                daysAgo % 2 == 0 ? SleepLatency.WITHIN_15 : SleepLatency.WITHIN_30,
                3 + (daysAgo % 3), // sleepSatisfaction 3~5
                daysAgo % 2 == 0 ? SugarIntake.NONE : SugarIntake.ONE_TO_TWO,
                switch (daysAgo % 3) {
                    case 0 -> CaffeineCups.NONE;
                    case 1 -> CaffeineCups.ONE_TO_TWO;
                    default -> CaffeineCups.THREE_TO_FOUR;
                },
                daysAgo % 2 == 0 ? CaffeineLastTime.MORNING : CaffeineLastTime.AFTERNOON,
                switch (daysAgo % 3) {
                    case 0 -> WaterIntake.THREE_TO_FIVE;
                    case 1 -> WaterIntake.SIX_TO_SEVEN;
                    default -> WaterIntake.EIGHT_OR_MORE;
                },
                exercised,
                exercised ? (daysAgo % 2 == 0 ? ExerciseDuration.ABOUT_30 : ExerciseDuration.ABOUT_60) : null,
                exercised
                        ? switch (daysAgo % 3) {
                            case 0 -> ExerciseType.WALKING;
                            case 1 -> ExerciseType.AEROBIC;
                            default -> ExerciseType.STRENGTH;
                        }
                        : null,
                daysAgo % 2 == 0 ? SittingHours.UNDER_4 : SittingHours.FOUR_TO_EIGHT,
                3 + (daysAgo % 5), // stressLevel 3~7
                switch (daysAgo % 3) {
                    case 0 -> MoodRecovery.NONE;
                    case 1 -> MoodRecovery.BRIEF;
                    default -> MoodRecovery.ENOUGH;
                },
                switch (daysAgo % 3) {
                    case 0 -> SocialContact.RARELY;
                    case 1 -> SocialContact.BRIEF;
                    default -> SocialContact.FREQUENT;
                },
                2 + (daysAgo % 2), // mealCount 2~3
                daysAgo % 2 == 0 ? WalkDuration.UNDER_30 : WalkDuration.THIRTY_TO_60,
                daysAgo % 2 == 0 ? ScreenTime.TWO_TO_FOUR : ScreenTime.FOUR_TO_SIX);
    }
}
