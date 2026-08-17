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
import cloud.anzaanza.antiagingdna.repository.DiaryRepository;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
import cloud.anzaanza.antiagingdna.service.AuthService;
import cloud.anzaanza.antiagingdna.service.DiaryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 개발용 시드 계정 — FE backend-backlog.md #17, 2026-08-17 결정("운영 DB에 시드 계정 심기").
 *
 * <p>가입자가 아직 없는 단계라 운영 DB에 직접 심어도 실사용자 데이터와 섞일 위험이 없다는
 * 판단에 따른 것이다. 부팅마다 실행되지만 {@link #LOGIN_ID} 계정과 {@link #SEED_DAYS}일치
 * 일지가 이미 있으면 그냥 끝난다 — 매번 다시 심지 않는다.
 *
 * <p>계정 존재 여부가 아니라 <b>날짜 하나하나의 존재 여부</b>로 재실행 가능하게 만든다.
 * 가입은 커밋됐는데 일지 14건 중 일부만 쓰고 프로세스가 죽는 경우(배포 재시작이 매번
 * 일어나는 단일 컨테이너 운영 구조에서 실제로 있을 수 있다) — 계정 존재만 보고 끝냈다면
 * 그 계정은 영영 일부 날짜가 빈 채로 남는다. 날짜 단위로 확인하면 다음 부팅이 빠진 날만
 * 채우고 끝난다.
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

    /**
     * 하루치 일지 3종. 매일 같은 값이면 캘린더·주간 그래프가 평평한 직선으로만 보여서 화면
     * 확인에 쓸모가 없다 — 값 자체에 의미를 담진 않는다(진짜 사용자 패턴을 흉내내려는 것이
     * 아니라 화면에 변화가 보이게 하는 것이 목적).
     */
    private static final List<DiaryRequest> DIARY_VARIANTS = List.of(
            new DiaryRequest(
                    5,
                    LocalTime.of(23, 30),
                    LocalTime.of(7, 0),
                    SleepLatency.WITHIN_15,
                    5,
                    SugarIntake.NONE,
                    CaffeineCups.NONE,
                    CaffeineLastTime.MORNING,
                    WaterIntake.EIGHT_OR_MORE,
                    true,
                    ExerciseDuration.ABOUT_30,
                    ExerciseType.WALKING,
                    SittingHours.UNDER_4,
                    3,
                    MoodRecovery.ENOUGH,
                    SocialContact.FREQUENT,
                    3,
                    WalkDuration.UNDER_30,
                    ScreenTime.TWO_TO_FOUR,
                    null,
                    null,
                    null),
            new DiaryRequest(
                    4,
                    LocalTime.of(0, 15),
                    LocalTime.of(7, 30),
                    SleepLatency.WITHIN_30,
                    4,
                    SugarIntake.ONE_TO_TWO,
                    CaffeineCups.ONE_TO_TWO,
                    CaffeineLastTime.AFTERNOON,
                    WaterIntake.SIX_TO_SEVEN,
                    true,
                    ExerciseDuration.ABOUT_60,
                    ExerciseType.AEROBIC,
                    SittingHours.FOUR_TO_EIGHT,
                    5,
                    MoodRecovery.BRIEF,
                    SocialContact.BRIEF,
                    2,
                    WalkDuration.THIRTY_TO_60,
                    ScreenTime.FOUR_TO_SIX,
                    null,
                    null,
                    null),
            new DiaryRequest(
                    3,
                    LocalTime.of(1, 0),
                    LocalTime.of(8, 0),
                    SleepLatency.OVER_60,
                    3,
                    SugarIntake.THREE_OR_MORE,
                    CaffeineCups.THREE_TO_FOUR,
                    CaffeineLastTime.EVENING,
                    WaterIntake.THREE_TO_FIVE,
                    false,
                    null,
                    null,
                    SittingHours.EIGHT_TO_TEN,
                    7,
                    MoodRecovery.NONE,
                    SocialContact.RARELY,
                    2,
                    WalkDuration.OVER_2_HOURS,
                    ScreenTime.OVER_6,
                    null,
                    null,
                    null));

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final AuthService authService;
    private final DiaryService diaryService;
    private final Clock clock;

    public DemoAccountSeeder(
            UserRepository userRepository,
            DiaryRepository diaryRepository,
            AuthService authService,
            DiaryService diaryService,
            Clock clock) {
        this.userRepository = userRepository;
        this.diaryRepository = diaryRepository;
        this.authService = authService;
        this.diaryService = diaryService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        User user = userRepository.findByLoginId(LOGIN_ID).orElseGet(() -> authService.signUp(signUpRequest()));

        LocalDate today = LocalDate.now(clock);
        for (int daysAgo = SEED_DAYS - 1; daysAgo >= 0; daysAgo--) {
            LocalDate date = today.minusDays(daysAgo);
            if (!diaryRepository.existsByAuthorIdAndLogDate(user.getId(), date)) {
                diaryService.save(user.getId(), date, DIARY_VARIANTS.get(daysAgo % DIARY_VARIANTS.size()));
            }
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
}
