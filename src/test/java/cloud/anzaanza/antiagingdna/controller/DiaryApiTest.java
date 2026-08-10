package cloud.anzaanza.antiagingdna.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cloud.anzaanza.antiagingdna.config.JwtProperties;
import cloud.anzaanza.antiagingdna.config.SecurityConfig;
import cloud.anzaanza.antiagingdna.dto.DiaryRequest;
import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.enums.SleepLatency;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import cloud.anzaanza.antiagingdna.exception.DiaryNotFoundException;
import cloud.anzaanza.antiagingdna.service.DiaryService;
import cloud.anzaanza.antiagingdna.service.TokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 일지 API — 인증·검증·응답 형태. 본문은 손으로 쓴 JSON 을 그대로 보낸다. */
@WebMvcTest(DiaryController.class)
@Import({SecurityConfig.class, TokenService.class, DiaryApiTest.FixedClock.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(
        properties = {
            "jwt.secret=테스트용-서명키-32바이트-이상이어야-한다-0123456789",
            "jwt.access-token-ttl=24h"
        })
class DiaryApiTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private TokenService tokenService;

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

    /** 목업 `01_CREATE_DIARY` 를 채웠을 때 클라이언트가 보내는 본문 */
    private static final String DIARY_JSON =
            """
            {"conditionLevel":4,
             "sleepStartedAt":"01:30","sleepEndedAt":"07:40",
             "sleepLatency":"WITHIN_5","sleepSatisfaction":3,
             "sugarIntake":"NONE","waterIntake":"UNDER_2",
             "exercised":true,"exerciseDuration":"UNDER_15","exerciseType":"WALKING",
             "sittingHours":"UNDER_4","stressLevel":7,
             "mealCount":0,"screenTime":"UNDER_2"}
            """;

    // ── 인증 ─────────────────────────────────────────────────────

    @Test
    void 토큰_없이는_일지를_읽거나_쓸_수_없다() throws Exception {
        mockMvc.perform(get("/api/diaries/2026-08-10")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/diaries/2026-08-10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DIARY_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── 저장 ─────────────────────────────────────────────────────

    @Test
    void 목업_그대로의_JSON_을_저장한다() throws Exception {
        given(diaryService.save(eq("user-1"), eq(TODAY), any(DiaryRequest.class))).willReturn(diary());

        mockMvc.perform(put("/api/diaries/2026-08-10")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DIARY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logDate").value("2026-08-10"))
                .andExpect(jsonPath("$.conditionLevel").value(4))
                // 취침·기상에서 파생 — 저장하지 않고 계산해서 내려준다 (01:30 → 07:40 = 370분)
                .andExpect(jsonPath("$.sleepMinutes").value(370));
    }

    /** 컨디션만 필수다. 부분 입력이 정상 경로여야 60초 안에 쓸 수 있다 */
    @Test
    void 컨디션만_보내도_저장된다() throws Exception {
        given(diaryService.save(eq("user-1"), eq(TODAY), any(DiaryRequest.class))).willReturn(diary());

        mockMvc.perform(put("/api/diaries/2026-08-10")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditionLevel\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void 컨디션이_없으면_400_과_필드명을_준다() throws Exception {
        mockMvc.perform(put("/api/diaries/2026-08-10")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.conditionLevel").isNotEmpty());
    }

    /** 스트레스는 0~10 NRS 표준이라 1~10 이다 — 목업의 0~100 슬라이더가 아니다 */
    @Test
    void 스트레스가_범위를_벗어나면_400_이다() throws Exception {
        mockMvc.perform(put("/api/diaries/2026-08-10")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditionLevel\":3,\"stressLevel\":85}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stressLevel").isNotEmpty());
    }

    @Test
    void 없는_선택지를_보내면_400_이다() throws Exception {
        mockMvc.perform(put("/api/diaries/2026-08-10")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditionLevel\":3,\"waterIntake\":\"MANY\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 일지는 EMA(당일 기록)다 — 미래를 미리 적으면 설계 전제가 깨진다 */
    @Test
    void 미래_날짜에는_쓸_수_없다() throws Exception {
        mockMvc.perform(put("/api/diaries/2026-08-11")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DIARY_JSON))
                .andExpect(status().isBadRequest());
    }

    // ── 조회·삭제 ────────────────────────────────────────────────

    @Test
    void 없는_날짜를_조회하면_404_다() throws Exception {
        willThrow(new DiaryNotFoundException(TODAY)).given(diaryService).get("user-1", TODAY);

        mockMvc.perform(get("/api/diaries/2026-08-10").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("일지 없음"));
    }

    @Test
    void 삭제하면_204_다() throws Exception {
        mockMvc.perform(delete("/api/diaries/2026-08-10").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 구간이_뒤집히면_400_이다() throws Exception {
        mockMvc.perform(get("/api/diaries")
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-01")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private static Diary diary() {
        return Diary.builder()
                .id("diary-1")
                .author(USER)
                .logDate(TODAY)
                .conditionLevel(4)
                .sleepStartedAt(LocalTime.of(1, 30))
                .sleepEndedAt(LocalTime.of(7, 40))
                .sleepLatency(SleepLatency.WITHIN_5)
                .sleepSatisfaction(3)
                .waterIntake(WaterIntake.UNDER_2)
                .stressLevel(7)
                .build();
    }
}
