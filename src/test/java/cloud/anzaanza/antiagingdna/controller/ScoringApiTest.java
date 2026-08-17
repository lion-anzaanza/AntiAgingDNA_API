package cloud.anzaanza.antiagingdna.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cloud.anzaanza.antiagingdna.config.JwtProperties;
import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.config.SecurityConfig;
import cloud.anzaanza.antiagingdna.dto.DnaInfoResponse;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import cloud.anzaanza.antiagingdna.exception.DiagnosisNotFoundException;
import cloud.anzaanza.antiagingdna.service.DiaryService;
import cloud.anzaanza.antiagingdna.service.DnaInfoService;
import cloud.anzaanza.antiagingdna.service.ScoringService;
import cloud.anzaanza.antiagingdna.service.TokenService;
import cloud.anzaanza.antiagingdna.service.scoring.BaselineCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** DNA·점수 조회 API — 인증 요구와 응답 형태. */
@WebMvcTest({DnaInfoController.class, ScoreController.class})
@Import({SecurityConfig.class, TokenService.class, ScoringApiTest.FixedClock.class})
@EnableConfigurationProperties({JwtProperties.class, ScoringProperties.class})
@TestPropertySource(
        properties = {
            "jwt.secret=테스트용-서명키-32바이트-이상이어야-한다-0123456789",
            "jwt.access-token-ttl=24h",
            "scoring.version=test-v1",
            "scoring.weights.physical=0.333",
            "scoring.weights.mental=0.278",
            "scoring.weights.emotion=0.222",
            "scoring.weights.social=0.167",
            "scoring.weights.environment=0",
            "scoring.alpha.shrinkage=7",
            "scoring.alpha.cap=0.95",
            "scoring.moving-average-days=7",
            "scoring.grade.good-min=70",
            "scoring.grade.warn-min=40"
        })
class ScoringApiTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);
    private static final ScoringProperties.GradeThresholds GRADE_THRESHOLDS =
            new ScoringProperties.GradeThresholds(new BigDecimal("70"), new BigDecimal("40"));

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private TokenService tokenService;

    @MockitoBean private DnaInfoService dnaInfoService;
    @MockitoBean private ScoringService scoringService;
    @MockitoBean private DiaryService diaryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final User USER = User.builder()
            .id("user-1")
            .email("nosleep@gmail.com")
            .password("hash")
            .nickname("안자안자")
            .birthYear(2002)
            .build();

    private String token() {
        return tokenService.issue(USER).value();
    }

    // ── 인증 ─────────────────────────────────────────────────────

    @Test
    void 토큰_없이는_DNA_도_점수도_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/dna")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/scores/today")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/scores/2026-08-10")).andExpect(status().isUnauthorized());
    }

    // ── DNA ──────────────────────────────────────────────────────

    @Test
    void DNA_조회는_원본_답변과_파생_baseline_을_함께_준다() throws Exception {
        DnaInfo dna = dna();
        given(dnaInfoService.myDna("user-1"))
                .willReturn(DnaInfoResponse.from(dna, BaselineCalculator.of(dna), GRADE_THRESHOLDS));

        mockMvc.perform(get("/api/dna").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sleepType").value("MORNING"))
                .andExpect(jsonPath("$.sleepIssues.daytimeDrowsy").value(true))
                // 수면질 75 · 운동 100 · 음주 100 · 흡연 100 → 93.75
                .andExpect(jsonPath("$.baseline.physical").value(93.75))
                .andExpect(jsonPath("$.baseline.grades.physical").value("GOOD"))
                .andExpect(jsonPath("$.baseline.social").doesNotExist())
                .andExpect(jsonPath("$.baseline.grades.social").doesNotExist())
                .andExpect(jsonPath("$.sensitivityCoefficients.caffeine").value(1.3));
    }

    @Test
    void 초기_진단이_없으면_404_다() throws Exception {
        given(dnaInfoService.myDna(anyString())).willThrow(new DiagnosisNotFoundException("user-1"));

        mockMvc.perform(get("/api/dna").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("초기 진단 없음"));
    }

    // ── 점수 ─────────────────────────────────────────────────────

    @Test
    void 오늘_점수는_일지가_없어도_표시값을_준다() throws Exception {
        given(scoringService.scoreOn("user-1", TODAY)).willReturn(score(TODAY, null, "93.75"));

        mockMvc.perform(get("/api/scores/today").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-10"))
                .andExpect(jsonPath("$.displayTotal").value(93.75))
                .andExpect(jsonPath("$.grade").value("GOOD"))
                .andExpect(jsonPath("$.dailyGrade").doesNotExist())
                .andExpect(jsonPath("$.orbState").value("GOOD_HIGH"))
                .andExpect(jsonPath("$.dailyTotal").doesNotExist())
                .andExpect(jsonPath("$.scoringVersion").value("test-v1"));
    }

    /** #32 — grade 는 baseline 이 섞인 displayTotal 기준이라 그날 기록과 반대로 보일 수 있다. dailyGrade 는 그날만의 기준 */
    @Test
    void 일지가_있는_날은_dailyGrade가_그날_기록_기준으로_따로_나온다() throws Exception {
        given(scoringService.scoreOn("user-1", TODAY)).willReturn(score(TODAY, "30.00", "93.75"));

        mockMvc.perform(get("/api/scores/today").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value("GOOD")) // displayTotal 93.75 기준
                .andExpect(jsonPath("$.dailyGrade").value("DANGER")); // dailyTotal 30.00 기준 — 그날은 나빴다
    }

    @Test
    void 미래_날짜는_400_이다() throws Exception {
        mockMvc.perform(get("/api/scores/2026-08-11").header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 구간_조회는_기록이_있는_날만_돌려준다() throws Exception {
        given(scoringService.scoresBetween("user-1", TODAY.minusDays(2), TODAY))
                .willReturn(List.of(score(TODAY.minusDays(2), "60.00", "80.00"), score(TODAY, "70.00", "82.00")));

        mockMvc.perform(get("/api/scores")
                        .param("from", "2026-08-08")
                        .param("to", "2026-08-10")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].dailyTotal").value(60.00))
                .andExpect(jsonPath("$[0].dailyGrade").value("WARN"));
    }

    @Test
    void from_이_to_보다_늦으면_400_이다() throws Exception {
        mockMvc.perform(get("/api/scores")
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-08")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 구간이_너무_길면_400_이다() throws Exception {
        mockMvc.perform(get("/api/scores")
                        .param("from", "2020-01-01")
                        .param("to", "2026-08-10")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    // ── 항목별 주간 추이 (수면·수분, FE backend-backlog #11/#27) ────

    @Test
    void 항목_구간_조회는_수면과_수분을_점수와_등급으로_함께_준다() throws Exception {
        Diary diary = Diary.builder()
                .logDate(TODAY)
                .sleepStartedAt(LocalTime.of(23, 0))
                .sleepEndedAt(LocalTime.of(7, 0)) // 8h → 100 → GOOD
                .waterIntake(WaterIntake.THREE_TO_FIVE) // 60 → WARN
                .build();
        given(diaryService.between("user-1", TODAY.minusDays(2), TODAY)).willReturn(List.of(diary));

        mockMvc.perform(get("/api/scores/items")
                        .param("from", "2026-08-08")
                        .param("to", "2026-08-10")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-08-10"))
                .andExpect(jsonPath("$[0].sleepMinutes").value(480))
                .andExpect(jsonPath("$[0].sleepScore").value(100.0))
                .andExpect(jsonPath("$[0].sleepGrade").value("GOOD"))
                .andExpect(jsonPath("$[0].waterIntake").value("THREE_TO_FIVE"))
                .andExpect(jsonPath("$[0].waterScore").value(60.0))
                .andExpect(jsonPath("$[0].waterGrade").value("WARN"));
    }

    @Test
    void 항목_구간_조회도_from_이_to_보다_늦으면_400_이다() throws Exception {
        mockMvc.perform(get("/api/scores/items")
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-08")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private static DnaInfo dna() {
        return DnaInfo.builder()
                .user(USER)
                .sleepType(SleepType.MORNING)
                .sleepDaytimeDrowsy(true) // 체크 1개 → 75
                .sleepOnsetDelayed(false)
                .sleepNightAwakening(false)
                .sleepUnrefreshed(false)
                .sugarSensitivity(SensitivityLevel.MODERATE)
                .caffeineSensitivity(SensitivityLevel.HIGH) // k = 1.3
                .stressSensitivity(SensitivityLevel.MODERATE)
                .exerciseLevel(ExerciseLevel.FROM_150_TO_300)
                .shiftWorker(false)
                .frequentTraveler(false)
                .drinkFrequency(DrinkFrequency.NEVER)
                .smokingStatus(SmokingStatus.NEVER)
                .lifeRhythm(LifeRhythm.VERY_REGULAR)
                .build();
    }

    private static DailyScore score(LocalDate date, String dailyTotal, String displayTotal) {
        return DailyScore.builder()
                .id("score-" + date)
                .user(USER)
                .scoreDate(date)
                .dailyTotal(dailyTotal == null ? null : new BigDecimal(dailyTotal))
                .displayTotal(new BigDecimal(displayTotal))
                .scoringVersion("test-v1")
                .build();
    }
}
