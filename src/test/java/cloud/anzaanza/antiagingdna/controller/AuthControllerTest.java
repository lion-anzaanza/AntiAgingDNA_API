package cloud.anzaanza.antiagingdna.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
                .willThrow(new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nosleep@gmail.com","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized());
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

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private static SignUpRequest signUpRequest(String password) {
        Map<AgreementType, Boolean> agreements = new EnumMap<>(AgreementType.class);
        for (AgreementType type : AgreementType.values()) {
            agreements.put(type, true);
        }
        return new SignUpRequest(
                "nosleep@gmail.com",
                password,
                "안자안자",
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
