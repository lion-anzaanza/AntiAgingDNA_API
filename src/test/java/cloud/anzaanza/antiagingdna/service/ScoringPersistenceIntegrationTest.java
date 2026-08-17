package cloud.anzaanza.antiagingdna.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cloud.anzaanza.antiagingdna.dto.DiagnosisRequest;
import cloud.anzaanza.antiagingdna.dto.DiaryRequest;
import cloud.anzaanza.antiagingdna.dto.SignUpRequest;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineCups;
import cloud.anzaanza.antiagingdna.entity.enums.CaffeineLastTime;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseDuration;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseType;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SittingHours;
import cloud.anzaanza.antiagingdna.entity.enums.SleepLatency;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContact;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContactLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SugarIntake;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import cloud.anzaanza.antiagingdna.exception.DiaryNotFoundException;
import cloud.anzaanza.antiagingdna.repository.DailyScoreRepository;
import cloud.anzaanza.antiagingdna.support.IntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 채점 파이프라인을 <b>실제 DB 위에서</b> 검증한다.
 *
 * <p>여기까지는 프로덕션에 요청을 쏴서 확인하던 것들이다. 느리고(배포 10분) 회귀를 막아주지도
 * 않았다. 단위 테스트는 리포지토리를 흉내 내므로 제약 위반·플러시 순서·영속성 컨텍스트 문제를
 * 볼 수 없다 — 그게 여기 있는 이유다.
 */
class ScoringPersistenceIntegrationTest extends IntegrationTest {

    /** 기획 §B-3 워크드 예시와 같은 조건: 민감도 전부 '보통'(k=1.1) */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private AuthService authService;
    @Autowired private DiaryService diaryService;
    @Autowired private ScoringService scoringService;
    @Autowired private DailyScoreRepository dailyScoreRepository;

    /**
     * "오늘"은 애플리케이션의 {@code Clock}(Asia/Seoul)에 물어봐야 한다.
     *
     * <p>{@code LocalDate.now()} 를 쓰면 JVM 기본 시간대를 따라간다. 개발 머신은 KST 라 늘
     * 통과하지만 CI runner 는 UTC 다 — 한국 시간 00:00\~09:00 사이에 돌면 앱이 쓴 날짜와
     * 테스트가 찾는 날짜가 하루 어긋난다. 실제로 첫 CI 실행(15:29 UTC = 익일 00:29 KST)이
     * 이 이유로 깨졌다.
     */
    @Autowired private Clock clock;

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private User newUser() {
        String suffix = SEQUENCE.incrementAndGet() + "-" + System.nanoTime();
        String loginId = "it" + suffix.replace("-", "");
        String email = "it-" + suffix + "@example.com";
        Map<AgreementType, Boolean> agreements = new EnumMap<>(AgreementType.class);
        for (AgreementType type : AgreementType.values()) {
            agreements.put(type, true);
        }
        return authService.signUp(new SignUpRequest(
                loginId.substring(0, Math.min(loginId.length(), 32)),
                email,
                "password1234",
                "통합테스트",
                2000,
                diagnosis(),
                agreements));
    }

    // ── 가입 ─────────────────────────────────────────────────────

    /** 가입 트랜잭션이 계정·진단·약관·day-0 점수를 한 번에 남기는지 (단위 테스트는 목이라 못 본다) */
    @Test
    void 가입하면_day0_점수까지_실제로_저장된다() {
        User user = newUser();

        DailyScore dayZero = dailyScoreRepository
                .findByUserIdAndScoreDate(user.getId(), today())
                .orElseThrow();

        assertThat(dayZero.getDisplayTotal()).isNotNull();
        assertThat(dayZero.getDailyTotal()).describedAs("일지가 없으니 당일값은 없다").isNull();
        assertThat(dayZero.getScoringVersion()).isNotBlank();
    }

    // ── 일지 → 채점 ──────────────────────────────────────────────

    /**
     * 기획 §B-3 워크드 예시를 실제 DB 왕복으로 재현한다.
     * 신체 67.31 · 정신 66.17 · 사회 60.00 (감정은 '기분 회복 활동' 앵커 미정이라 스트레스 단일 소스)
     *
     * <p>정신 영역은 원래 기획 §B-3 예시값(67.0, 스트레스 1~10 도메인 기준)과 거의 일치하는
     * 66.98이었으나, stressLevel 도메인을 0~10으로 확장(2026-08-17, A-6 결정)하면서 같은
     * 입력(6)의 스트레스 원점수가 44.44→40.0으로 바뀌어 66.17로 이동했다.
     */
    @Test
    void 기획_B3_예시가_DB_왕복_뒤에도_그대로_재현된다() {
        User user = newUser();
        LocalDate today = today();

        diaryService.save(user.getId(), today, workedExampleDiary());

        DailyScore score = dailyScoreRepository
                .findByUserIdAndScoreDate(user.getId(), today)
                .orElseThrow();

        assertThat(score.getPhysicalScore()).isEqualByComparingTo("67.31");
        assertThat(score.getMentalScore()).isEqualByComparingTo("66.17");
        assertThat(score.getSocialScore()).isEqualByComparingTo("60.00");
        assertThat(score.getEnvironmentScore()).describedAs("환경은 입력이 없어 결측").isNull();
    }

    /** 같은 날짜에 두 번 저장해도 하루 1건 제약({@code uk_diary_author_log_date})에 걸리지 않아야 한다 */
    @Test
    void 같은_날짜에_다시_저장해도_제약_위반이_없다() {
        User user = newUser();
        LocalDate today = today();

        diaryService.save(user.getId(), today, workedExampleDiary());
        diaryService.save(user.getId(), today, workedExampleDiary());

        assertThat(dailyScoreRepository.findByUserIdOrderByScoreDateAsc(user.getId()))
                .describedAs("점수 행도 하루 1건이어야 한다")
                .hasSize(1);
    }

    /**
     * 자정 넘겨 어제 일지를 쓰면 오늘 점수의 7일 이동평균이 바뀐다. 단위 테스트는 호출만
     * 확인했고, 값이 실제로 달라지는지는 여기서만 볼 수 있다.
     */
    @Test
    void 과거_날짜_일지가_이미_저장된_뒤_날짜_점수를_바꾼다() {
        User user = newUser();
        LocalDate today = today();
        LocalDate yesterday = today.minusDays(1);

        var beforeBackfill = scoringService.scoreOn(user.getId(), today).getDisplayTotal();

        diaryService.save(user.getId(), yesterday, workedExampleDiary());

        var afterBackfill = dailyScoreRepository
                .findByUserIdAndScoreDate(user.getId(), today)
                .orElseThrow()
                .getDisplayTotal();

        assertThat(afterBackfill)
                .describedAs("어제 일지가 오늘 이동평균에 반영돼야 한다")
                .isNotEqualByComparingTo(beforeBackfill);
    }

    @Test
    void 일지를_지우면_baseline_점수로_돌아간다() {
        User user = newUser();
        LocalDate today = today();

        var baseline = scoringService.scoreOn(user.getId(), today).getDisplayTotal();
        diaryService.save(user.getId(), today, workedExampleDiary());
        diaryService.delete(user.getId(), today);

        DailyScore after = dailyScoreRepository.findByUserIdAndScoreDate(user.getId(), today).orElseThrow();
        assertThat(after.getDisplayTotal()).isEqualByComparingTo(baseline);
        assertThat(after.getPhysicalScore()).isNull();
        assertThatThrownBy(() -> diaryService.get(user.getId(), today))
                .isInstanceOf(DiaryNotFoundException.class);
    }

    // ── 재채점 ───────────────────────────────────────────────────

    /** 파라미터가 그대로면 몇 번을 다시 돌려도 같은 값이어야 한다 */
    @Test
    void 재채점은_멱등하다() {
        User user = newUser();
        LocalDate today = today();
        diaryService.save(user.getId(), today, workedExampleDiary());

        List<DailyScore> before = dailyScoreRepository.findByUserIdOrderByScoreDateAsc(user.getId());
        var beforeTotals = before.stream().map(DailyScore::getDisplayTotal).toList();

        scoringService.rescore(user.getId());

        var afterTotals = dailyScoreRepository.findByUserIdOrderByScoreDateAsc(user.getId()).stream()
                .map(DailyScore::getDisplayTotal)
                .toList();
        assertThat(afterTotals).containsExactlyElementsOf(beforeTotals);
    }

    /** 재채점이 행을 새로 만들면 하루 1건 unique 제약에 걸린다 */
    @Test
    void 재채점은_행을_새로_만들지_않는다() {
        User user = newUser();
        diaryService.save(user.getId(), today(), workedExampleDiary());
        var idsBefore = dailyScoreRepository.findByUserIdOrderByScoreDateAsc(user.getId()).stream()
                .map(DailyScore::getId)
                .toList();

        scoringService.rescore(user.getId());

        assertThat(dailyScoreRepository.findByUserIdOrderByScoreDateAsc(user.getId()))
                .extracting(DailyScore::getId)
                .containsExactlyElementsOf(idsBefore);
    }

    // ── 고정 데이터 ──────────────────────────────────────────────

    private static DiagnosisRequest diagnosis() {
        return new DiagnosisRequest(
                SleepType.MORNING, true, false, false, false,
                SensitivityLevel.MODERATE, SensitivityLevel.MODERATE, SensitivityLevel.MODERATE,
                ExerciseLevel.FROM_150_TO_300, false, false,
                DrinkFrequency.MONTHLY_OR_LESS, SmokingStatus.NEVER, LifeRhythm.MOSTLY_REGULAR,
                SocialContactLevel.THREE_TO_FOUR_PER_WEEK, 4, 4, 4, 4, 3);
    }

    private static DiaryRequest workedExampleDiary() {
        return new DiaryRequest(
                4,
                LocalTime.of(23, 0), LocalTime.of(6, 10), // 7h10m → 100
                SleepLatency.WITHIN_15, // 75
                3, // 수면 만족 보통 → 50
                SugarIntake.ONE_TO_TWO, // 55 → k1.1 → 50.5
                CaffeineCups.ONE_TO_TWO, CaffeineLastTime.AFTERNOON, // avg 80 → k1.1 → 78
                WaterIntake.SIX_TO_SEVEN, // 85
                true, ExerciseDuration.ABOUT_30, ExerciseType.AEROBIC, // 55
                SittingHours.EIGHT_TO_TEN, // 45
                6, // 스트레스 → 40.0 → k1.1 → 34.0
                null,
                SocialContact.BRIEF, // 60
                3, null, null,
                null, null, null);
    }
}
