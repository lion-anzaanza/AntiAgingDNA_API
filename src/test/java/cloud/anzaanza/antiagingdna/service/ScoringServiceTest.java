package cloud.anzaanza.antiagingdna.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.config.ScoringProperties.Alpha;
import cloud.anzaanza.antiagingdna.config.ScoringProperties.Weights;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SleepLatency;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.exception.DiagnosisNotFoundException;
import cloud.anzaanza.antiagingdna.repository.DailyScoreRepository;
import cloud.anzaanza.antiagingdna.repository.DiaryRepository;
import cloud.anzaanza.antiagingdna.repository.DnaInfoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 파이프라인 ③~⑥ 단계의 조립 — 저장 형태와 결합 순서를 본다. */
@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    private static final String USER_ID = "user-1";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

    private static final ScoringProperties PROPERTIES = new ScoringProperties(
            "test-v1",
            new Weights(
                    new BigDecimal("0.333"),
                    new BigDecimal("0.278"),
                    new BigDecimal("0.222"),
                    new BigDecimal("0.167"),
                    BigDecimal.ZERO),
            new Alpha(7, new BigDecimal("0.95")),
            7);

    @Mock private DnaInfoRepository dnaInfoRepository;
    @Mock private DiaryRepository diaryRepository;
    @Mock private DailyScoreRepository dailyScoreRepository;

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService =
                new ScoringService(dnaInfoRepository, diaryRepository, dailyScoreRepository, PROPERTIES);
    }

    private static final User USER = User.builder()
            .id(USER_ID)
            .email("nosleep@gmail.com")
            .password("hash")
            .nickname("안자안자")
            .birthYear(2002)
            .build();

    /** 신체 baseline = avg(수면질 100, 운동 100, 음주 100, 흡연 100) = 100 — 계산을 단순하게 두기 위해 */
    private static DnaInfo perfectDna() {
        return DnaInfo.builder()
                .user(USER)
                .sleepType(SleepType.MORNING)
                .sleepDaytimeDrowsy(false)
                .sleepOnsetDelayed(false)
                .sleepNightAwakening(false)
                .sleepUnrefreshed(false)
                .sugarSensitivity(SensitivityLevel.MODERATE)
                .caffeineSensitivity(SensitivityLevel.MODERATE)
                .stressSensitivity(SensitivityLevel.MODERATE)
                .exerciseLevel(ExerciseLevel.FROM_150_TO_300)
                .shiftWorker(false)
                .frequentTraveler(false)
                .drinkFrequency(DrinkFrequency.NEVER)
                .smokingStatus(SmokingStatus.NEVER)
                .lifeRhythm(LifeRhythm.VERY_REGULAR)
                .build();
    }

    private void givenDna() {
        given(dnaInfoRepository.findById(USER_ID)).willReturn(Optional.of(perfectDna()));
    }

    private void givenSaveReturnsArgument() {
        given(dailyScoreRepository.save(any(DailyScore.class))).willAnswer(call -> call.getArgument(0));
    }

    // ── day-0 ────────────────────────────────────────────────────

    @Test
    void 일지가_없는_날도_baseline_만으로_표시점수가_나온다() {
        givenDna();
        givenSaveReturnsArgument();
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());

        DailyScore score = scoringService.recalculate(USER_ID, TODAY);

        assertThat(score.getDisplayTotal()).describedAs("표시 점수는 항상 있어야 한다").isNotNull();
        assertThat(score.getDisplayTotal()).isEqualByComparingTo("100.00");
        assertThat(score.getDailyTotal()).describedAs("일지가 없으니 당일값은 없다").isNull();
        assertThat(score.getPhysicalScore()).isNull();
        assertThat(score.getScoringVersion()).isEqualTo("test-v1");
        assertThat(score.getScoreDate()).isEqualTo(TODAY);
    }

    // ── 일지가 있는 날 ───────────────────────────────────────────

    @Test
    void 일지가_있으면_당일_영역점수와_당일총점이_함께_저장된다() {
        givenDna();
        givenSaveReturnsArgument();
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.of(diary()));
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());

        DailyScore score = scoringService.recalculate(USER_ID, TODAY);

        // 수면 두 항목은 신체와 정신 양쪽에 들어간다 (기획 §B-2)
        assertThat(score.getPhysicalScore()).isEqualByComparingTo("87.50"); // avg(수면시간 100, 잠들기 75)
        assertThat(score.getMentalScore()).isEqualByComparingTo("87.50");
        assertThat(score.getEmotionScore()).describedAs("스트레스 미입력 → 감정은 결측").isNull();
        assertThat(score.getDailyTotal()).isEqualByComparingTo("87.50");

        // baseline 은 신체·정신·감정 모두 100. 신체·정신은 n=1 → α=0.125 로 87.5 쪽으로 조금
        // 당겨지고(98.4375), 감정은 일지 소스가 없어 baseline 100 그대로다.
        // (0.333+0.278)×98.4375 + 0.222×100 을 (0.333+0.278+0.222) 로 나누면 98.85.
        assertThat(score.getDisplayTotal().doubleValue()).isCloseTo(98.85, within(0.01));
    }

    /** α(n) = n/(n+7) — 기록이 쌓일수록 표시 점수가 일지 쪽으로 단조 이동해야 한다 */
    @Test
    void 기록이_쌓일수록_표시점수가_일지_쪽으로_이동한다() {
        double firstDay = displayTotalWithHistory(0L, List.of());
        double afterSixDays = displayTotalWithHistory(6L, List.of());
        double afterThirtyDays = displayTotalWithHistory(30L, List.of());

        assertThat(afterSixDays).isLessThan(firstDay);
        assertThat(afterThirtyDays).isLessThan(afterSixDays);
        // baseline 100 · 일지 87.5 이므로 그 사이를 벗어나지 않는다
        assertThat(afterThirtyDays).isGreaterThan(87.5);
    }

    /** 이동평균 창은 오늘을 포함한 7일 — 저장 전인 오늘 값도 평균에 들어가야 한다 */
    @Test
    void 이동평균은_지난_기록과_오늘_값을_함께_평균낸다() {
        double withoutHistory = displayTotalWithHistory(6L, List.of());
        double withLowerYesterday =
                displayTotalWithHistory(6L, List.of(previousScore(TODAY.minusDays(1), "62.50")));

        assertThat(withLowerYesterday)
                .describedAs("어제가 낮았으면 오늘 표시 점수도 끌려 내려간다")
                .isLessThan(withoutHistory);
    }

    private double displayTotalWithHistory(long recordedDays, List<DailyScore> window) {
        givenDna();
        givenSaveReturnsArgument();
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.of(diary()));
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        USER_ID, TODAY.minusDays(6), TODAY.minusDays(1)))
                .willReturn(window);
        given(dailyScoreRepository.countByUserIdAndScoreDateLessThanAndPhysicalScoreIsNotNull(
                        USER_ID, TODAY))
                .willReturn(recordedDays);
        given(dailyScoreRepository.countByUserIdAndScoreDateLessThanAndMentalScoreIsNotNull(
                        USER_ID, TODAY))
                .willReturn(recordedDays);

        return scoringService.recalculate(USER_ID, TODAY).getDisplayTotal().doubleValue();
    }

    // ── 갱신 ─────────────────────────────────────────────────────

    @Test
    void 같은_날짜를_다시_계산하면_기존_행을_갱신한다() {
        givenDna();
        givenSaveReturnsArgument();
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY))
                .willReturn(Optional.of(previousScore(TODAY, "50.00")));
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());

        DailyScore score = scoringService.recalculate(USER_ID, TODAY);

        assertThat(score.getId())
                .describedAs("행을 새로 만들지 않고 덮어쓴다 — 하루 1행 제약이 있다")
                .isEqualTo("existing-score");
    }

    // ── 조회 ─────────────────────────────────────────────────────

    @Test
    void 행이_없으면_조회_시점에_산출해_채운다() {
        givenDna();
        givenSaveReturnsArgument();
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());

        assertThat(scoringService.scoreOn(USER_ID, TODAY).getDisplayTotal()).isNotNull();
    }

    @Test
    void 행이_이미_있으면_다시_계산하지_않는다() {
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY))
                .willReturn(Optional.of(previousScore(TODAY, "50.00")));

        assertThat(scoringService.scoreOn(USER_ID, TODAY).getDisplayTotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void 초기_진단이_없으면_404_로_떨어진다() {
        given(dnaInfoRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scoringService.recalculate(USER_ID, TODAY))
                .isInstanceOf(DiagnosisNotFoundException.class);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    /** 신체·정신에 수면 두 항목만 있는 일지 — 신체 = avg(100, 75) = 87.5 */
    private static Diary diary() {
        return Diary.builder()
                .author(USER)
                .logDate(TODAY)
                .conditionLevel(3)
                .sleepStartedAt(LocalTime.of(23, 0))
                .sleepEndedAt(LocalTime.of(7, 0)) // 8h → 100
                .sleepLatency(SleepLatency.WITHIN_15) // 75
                .build();
    }

    private static DailyScore previousScore(LocalDate date, String physical) {
        return DailyScore.builder()
                .id("existing-score")
                .user(USER)
                .scoreDate(date)
                .physicalScore(new BigDecimal(physical))
                .displayTotal(new BigDecimal(physical))
                .scoringVersion("test-v1")
                .build();
    }
}
