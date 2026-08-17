package cloud.anzaanza.antiagingdna.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cloud.anzaanza.antiagingdna.config.JwtProperties;
import cloud.anzaanza.antiagingdna.config.SecurityConfig;
import cloud.anzaanza.antiagingdna.dto.DiagnosisRequest;
import cloud.anzaanza.antiagingdna.dto.SignUpRequest;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.exception.EmailAlreadyUsedException;
import cloud.anzaanza.antiagingdna.service.AuthService;
import cloud.anzaanza.antiagingdna.service.TokenService;
// Spring Boot 4 는 Jackson 3(tools.jackson)을 쓴다 — com.fasterxml 쪽 ObjectMapper 빈은 없다.
import tools.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 컨트롤러와 <b>시큐리티 필터 체인</b>을 함께 검증한다.
 *
 * <p>DB 가 없어 {@code @SpringBootTest} 를 띄울 수 없다. 그래서 인증 설정이 실제로 어떻게
 * 동작하는지 확인할 수 있는 지점이 여기뿐이다 — 2026-08-10 의 Flyway 사고처럼, 설정이 조용히
 * 빠진 채 배포되어 운영에서 처음 드러나는 일을 막는 자리다.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, TokenService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(
        properties = {
            "jwt.secret=테스트용-서명키-32바이트-이상이어야-한다-0123456789",
            "jwt.access-token-ttl=24h"
        })
class AuthControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TokenService tokenService;

    @MockitoBean private AuthService authService;

    private MockMvc mockMvc;

    /**
     * 자동 구성된 MockMvc 는 시큐리티 필터를 태우지 않는다. {@code springSecurity()} 를 붙여야
     * 실제 요청과 같은 경로(Bearer 파싱 → 서명 검증 → 인가)를 지난다.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static User user() {
        return User.builder()
                .id("user-1")
                .loginId("nosleep_dev")
                .email("nosleep@gmail.com")
                .password("$2a$10$hash")
                .nickname("안자안자")
                .birthYear(2002)
                .build();
    }

    // ── 공개 엔드포인트 ───────────────────────────────────────────

    @Test
    void 회원가입은_토큰_없이_호출되고_토큰을_돌려준다() throws Exception {
        given(authService.signUp(any(SignUpRequest.class))).willReturn(user());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("password1234"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.user.nickname").value("안자안자"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void 비밀번호가_짧으면_400_과_필드별_오류를_준다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    void 닉네임이_한_글자면_400_이다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("password1234", "안"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nickname").isNotEmpty());
    }

    @Test
    void 닉네임에_특수문자가_있으면_400_이다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("password1234", "안자!!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nickname").isNotEmpty());
    }

    @Test
    void 이메일이_중복이면_409_다() throws Exception {
        willThrow(new EmailAlreadyUsedException("nosleep@gmail.com"))
                .given(authService)
                .signUp(any(SignUpRequest.class));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("password1234"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("이메일 중복"));
    }

    @Test
    void 로그인_실패는_401_이다() throws Exception {
        given(authService.authenticate(anyString(), anyString()))
                .willThrow(new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"nosleep_dev","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized());
    }

    // ── 실제 전송 형식 ───────────────────────────────────────────

    /**
     * <b>DTO 를 직렬화해서 되던지지 않고</b> 클라이언트가 보낼 JSON 문자열을 그대로 보낸다.
     *
     * <p>2026-08-10 배포에서 가입이 전부 400 이었다. 직렬화 왕복 테스트는 항상 모든 필드를
     * 채워 보내기 때문에, 필드가 빠졌을 때 Jackson 3 이 원시 타입 {@code boolean} 에 null 을
     * 넣지 못해 터지는 것을 볼 수 없었다. 손으로 쓴 JSON 만이 그 경로를 지난다.
     */
    @Test
    void 클라이언트가_보내는_JSON_을_그대로_받는다() throws Exception {
        given(authService.signUp(any(SignUpRequest.class))).willReturn(user());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void 진단_항목이_빠지면_어느_항목인지_알려준다() throws Exception {
        String missingCheckbox = SIGNUP_JSON.replace("\"sleepDaytimeDrowsy\":true,", "");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingCheckbox))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['diagnosis.sleepDaytimeDrowsy']").isNotEmpty());
    }

    // ── 보호된 엔드포인트 ─────────────────────────────────────────

    @Test
    void me_는_토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_는_유효한_토큰이면_계정을_돌려준다() throws Exception {
        given(authService.findById("user-1")).willReturn(user());
        String token = tokenService.issue(user()).value();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.email").value("nosleep@gmail.com"));
    }

    @Test
    void me_는_서명이_다른_토큰을_거부한다() throws Exception {
        JwtProperties other = new JwtProperties("전혀-다른-서명키-32바이트-이상이어야-한다-98765", java.time.Duration.ofHours(1));
        String forged = new TokenService(
                        new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                                new com.nimbusds.jose.jwk.source.ImmutableSecret<>(other.secretKey())),
                        other)
                .issue(user())
                .value();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴는_토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(delete("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴는_유효한_토큰이면_204_를_돌려준다() throws Exception {
        String token = tokenService.issue(user()).value();

        mockMvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // ── 공개 엔드포인트: 중복 확인 ─────────────────────────────────

    @Test
    void 이메일_중복_확인은_토큰_없이_호출된다() throws Exception {
        given(authService.emailExists("nosleep@gmail.com")).willReturn(true);

        mockMvc.perform(get("/api/auth/check-email").param("email", "nosleep@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 아이디_중복_확인은_토큰_없이_호출된다() throws Exception {
        given(authService.loginIdExists("nosleep_dev")).willReturn(false);

        mockMvc.perform(get("/api/auth/check-login-id").param("loginId", "nosleep_dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    /**
     * 형식이 가입 검증을 통과 못 할 값은 중복 확인 단계에서부터 400 이어야 한다 — 안 그러면
     * "사용 가능"으로 보였다가 실제 가입 제출에서 400 이 나는 모순이 생긴다.
     */
    @Test
    void 이메일_형식이_잘못되면_중복_확인도_400_이다() throws Exception {
        mockMvc.perform(get("/api/auth/check-email").param("email", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 아이디_형식이_잘못되면_중복_확인도_400_이다() throws Exception {
        mockMvc.perform(get("/api/auth/check-login-id").param("loginId", "a"))
                .andExpect(status().isBadRequest());
    }

    /** 회원탈퇴 중 동시 쓰기가 FK/유니크 제약과 충돌하면 스택트레이스 500 이 아니라 409 여야 한다 */
    @Test
    void 탈퇴_중_DB_제약_충돌은_409_다() throws Exception {
        willThrow(new DataIntegrityViolationException("constraint violation"))
                .given(authService)
                .withdraw(anyString());
        String token = tokenService.issue(user()).value();

        mockMvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    /** 목업 STEP 2·3·4 를 채웠을 때 클라이언트가 실제로 보내는 본문 */
    private static final String SIGNUP_JSON =
            """
            {"loginId":"nosleep_dev","email":"nosleep@gmail.com","password":"password1234","nickname":"안자안자","birthYear":2002,
             "diagnosis":{"sleepType":"MORNING",
              "sleepDaytimeDrowsy":true,"sleepOnsetDelayed":false,
              "sleepNightAwakening":false,"sleepUnrefreshed":false,
              "sugarSensitivity":"NONE","caffeineSensitivity":"HIGH","stressSensitivity":"MODERATE",
              "exerciseLevel":"FROM_150_TO_300","shiftWorker":false,"frequentTraveler":false,
              "drinkFrequency":"MONTHLY_OR_LESS","smokingStatus":"CURRENT_DAILY",
              "lifeRhythm":"VERY_REGULAR"},
             "agreements":{"TERMS_OF_SERVICE":true,"PRIVACY_SENSITIVE":true,
              "MARKETING":false,"AGE_OVER_14":true}}
            """;

    private static SignUpRequest signUpRequest(String password) {
        return signUpRequest(password, "안자안자");
    }

    private static SignUpRequest signUpRequest(String password, String nickname) {
        Map<AgreementType, Boolean> agreements = new EnumMap<>(AgreementType.class);
        for (AgreementType type : AgreementType.values()) {
            agreements.put(type, true);
        }
        return new SignUpRequest(
                "nosleep_dev",
                "nosleep@gmail.com",
                password,
                nickname,
                2002,
                new DiagnosisRequest(
                        SleepType.MORNING,
                        true,
                        false,
                        false,
                        false,
                        SensitivityLevel.NONE,
                        SensitivityLevel.HIGH,
                        SensitivityLevel.MODERATE,
                        ExerciseLevel.FROM_150_TO_300,
                        false,
                        false,
                        DrinkFrequency.MONTHLY_OR_LESS,
                        SmokingStatus.CURRENT_DAILY,
                        LifeRhythm.VERY_REGULAR,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                agreements);
    }
}
