package cloud.anzaanza.antiagingdna.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.config.ScoringProperties.Alpha;
import cloud.anzaanza.antiagingdna.config.ScoringProperties.GradeThresholds;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
            7,
            new GradeThresholds(new BigDecimal("70"), new BigDecimal("40")));

    private static final Clock CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    @Mock private DnaInfoRepository dnaInfoRepository;
    @Mock private DiaryRepository diaryRepository;
    @Mock private DailyScoreRepository dailyScoreRepository;

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService =
                new ScoringService(dnaInfoRepository, diaryRepository, dailyScoreRepository, PROPERTIES, CLOCK);
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

    /**
     * #31 — 일지가 없는 <b>과거</b> 날짜는 조회만 해서는 행을 만들지 않는다
     * (FE backend-backlog.md #31, 캘린더가 빈 날들을 조회만 해도 그 달이 전부
     * "기록 있는 달"이 되던 문제).
     */
    @Test
    void 일지가_없는_과거_날짜는_조회해도_저장하지_않는다() {
        LocalDate pastDate = TODAY.minusDays(10);
        givenDna();
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, pastDate)).willReturn(Optional.empty());
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, pastDate)).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());

        DailyScore result = scoringService.scoreOn(USER_ID, pastDate);

        assertThat(result.getDisplayTotal()).isNotNull();
        verify(dailyScoreRepository, never()).save(any(DailyScore.class));
    }

    /** 오늘 날짜는 일지가 없어도 저장한다 — 홈 오브 카드가 매번 조회하는 자리라 캐시해 둘 가치가 있다 */
    @Test
    void 오늘_날짜는_일지가_없어도_조회_시점에_저장된다() {
        givenDna();
        givenSaveReturnsArgument();
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());

        scoringService.scoreOn(USER_ID, TODAY);

        verify(dailyScoreRepository).save(any(DailyScore.class));
    }

    /** 일지가 있는 날짜는 그대로 캐시해 둔다 — read-through 는 실제 기록이 있는 날에만 적용된다 */
    @Test
    void 일지가_있는_과거_날짜는_조회_시점에_저장된다() {
        LocalDate pastDate = TODAY.minusDays(10);
        givenDna();
        givenSaveReturnsArgument();
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, pastDate)).willReturn(Optional.empty());
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, pastDate)).willReturn(Optional.of(diary()));
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());

        scoringService.scoreOn(USER_ID, pastDate);

        verify(dailyScoreRepository).save(any(DailyScore.class));
    }

    @Test
    void 행이_이미_있으면_다시_계산하지_않는다() {
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY))
                .willReturn(Optional.of(previousScore(TODAY, "50.00")));

        assertThat(scoringService.scoreOn(USER_ID, TODAY).getDisplayTotal()).isEqualByComparingTo("50.00");
    }

    // ── 과거 날짜 일지 수정 → 뒤 날짜 파급 ───────────────────────

    /**
     * 자정 넘겨 어제 일지를 쓰는 것은 정상 경로다({@code log_date} 가 작성 시각과 별개).
     * 그때 그 날짜만 고치면 오늘 점수의 7일 이동평균이 어제 값을 반영하지 못한 채 남는다.
     */
    @Test
    void 과거_날짜를_고치면_그_뒤_날짜도_다시_산출된다() {
        LocalDate yesterday = TODAY.minusDays(1);
        givenDna();
        givenSaveReturnsArgument();
        given(diaryRepository.findByAuthorIdAndLogDate(anyString(), any())).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDate(anyString(), any())).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());
        given(dailyScoreRepository.findByUserIdAndScoreDateGreaterThanOrderByScoreDateAsc(
                        USER_ID, yesterday))
                .willReturn(List.of(previousScore(TODAY, "50.00")));

        assertThat(scoringService.recalculateFrom(USER_ID, yesterday)).isEqualTo(2);

        ArgumentCaptor<DailyScore> saved = ArgumentCaptor.forClass(DailyScore.class);
        verify(dailyScoreRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(DailyScore::getScoreDate)
                .describedAs("바뀐 날짜 → 그 뒤 날짜 순서로")
                .containsExactly(yesterday, TODAY);
    }

    /** 오늘 일지를 쓰는 일반적인 경우엔 뒤 날짜가 없으므로 한 번만 돈다 */
    @Test
    void 오늘_날짜를_고치면_한_번만_산출한다() {
        givenDna();
        givenSaveReturnsArgument();
        given(diaryRepository.findByAuthorIdAndLogDate(anyString(), any())).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDate(anyString(), any())).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());
        given(dailyScoreRepository.findByUserIdAndScoreDateGreaterThanOrderByScoreDateAsc(USER_ID, TODAY))
                .willReturn(List.of());

        assertThat(scoringService.recalculateFrom(USER_ID, TODAY)).isEqualTo(1);
        verify(dailyScoreRepository, times(1)).save(any(DailyScore.class));
    }

    // ── 재채점 (scoring.version 변경 후) ─────────────────────────

    @Test
    void 옛_버전_행이_남아_있으면_조회_시점에_다시_만든다() {
        givenDna();
        givenSaveReturnsArgument();
        given(dailyScoreRepository.existsByUserIdAndScoringVersionNot(USER_ID, "test-v1"))
                .willReturn(true);
        given(dailyScoreRepository.findByUserIdOrderByScoreDateAsc(USER_ID))
                .willReturn(List.of(
                        oldVersionScore(TODAY.minusDays(2)),
                        oldVersionScore(TODAY.minusDays(1)),
                        oldVersionScore(TODAY)));
        given(diaryRepository.findByAuthorIdAndLogDate(anyString(), any())).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());
        given(dailyScoreRepository.findByUserIdAndScoreDate(anyString(), any()))
                .willAnswer(call -> Optional.of(oldVersionScore(call.getArgument(1))));

        scoringService.scoreOn(USER_ID, TODAY);

        // 3일치가 전부 현재 버전으로 다시 저장된다
        ArgumentCaptor<DailyScore> saved = ArgumentCaptor.forClass(DailyScore.class);
        verify(dailyScoreRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues())
                .allSatisfy(score -> assertThat(score.getScoringVersion()).isEqualTo("test-v1"));
    }

    /**
     * 결합값이 앞선 6일의 영역 점수를 평균하므로, 뒤에서부터 고치면 아직 옛 파라미터로 계산된
     * 이웃을 읽는다. 오름차순은 취향이 아니라 정확성 요건이다.
     */
    @Test
    void 재채점은_날짜_오름차순으로_진행된다() {
        givenDna();
        givenSaveReturnsArgument();
        given(dailyScoreRepository.findByUserIdOrderByScoreDateAsc(USER_ID))
                .willReturn(List.of(
                        oldVersionScore(TODAY.minusDays(2)),
                        oldVersionScore(TODAY.minusDays(1)),
                        oldVersionScore(TODAY)));
        given(diaryRepository.findByAuthorIdAndLogDate(anyString(), any())).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());

        assertThat(scoringService.rescore(USER_ID)).isEqualTo(3);

        ArgumentCaptor<DailyScore> saved = ArgumentCaptor.forClass(DailyScore.class);
        verify(dailyScoreRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(DailyScore::getScoreDate)
                .containsExactly(TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
    }

    @Test
    void 버전이_같으면_재채점하지_않는다() {
        given(dailyScoreRepository.existsByUserIdAndScoringVersionNot(USER_ID, "test-v1"))
                .willReturn(false);
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY))
                .willReturn(Optional.of(previousScore(TODAY, "50.00")));

        scoringService.scoreOn(USER_ID, TODAY);

        verify(dailyScoreRepository, never()).findByUserIdOrderByScoreDateAsc(anyString());
        verify(dailyScoreRepository, never()).save(any(DailyScore.class));
    }

    @Test
    void 재채점은_기존_행을_덮어쓴다() {
        givenDna();
        givenSaveReturnsArgument();
        given(dailyScoreRepository.findByUserIdOrderByScoreDateAsc(USER_ID))
                .willReturn(List.of(oldVersionScore(TODAY)));
        given(diaryRepository.findByAuthorIdAndLogDate(anyString(), any())).willReturn(Optional.empty());
        given(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        anyString(), any(), any()))
                .willReturn(List.of());
        given(dailyScoreRepository.findByUserIdAndScoreDate(USER_ID, TODAY))
                .willReturn(Optional.of(oldVersionScore(TODAY)));

        scoringService.rescore(USER_ID);

        ArgumentCaptor<DailyScore> saved = ArgumentCaptor.forClass(DailyScore.class);
        verify(dailyScoreRepository).save(saved.capture());
        assertThat(saved.getValue().getId())
                .describedAs("행을 새로 만들면 하루 1행 제약에 걸린다")
                .isEqualTo("existing-score");
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

    /** 파라미터가 바뀌기 전 버전으로 저장돼 있던 행 */
    private static DailyScore oldVersionScore(LocalDate date) {
        return DailyScore.builder()
                .id("existing-score")
                .user(USER)
                .scoreDate(date)
                .displayTotal(new BigDecimal("50.00"))
                .scoringVersion("test-v0")
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
