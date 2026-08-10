package cloud.anzaanza.antiagingdna.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.anzaanza.antiagingdna.dto.DiagnosisRequest;
import cloud.anzaanza.antiagingdna.dto.SignUpRequest;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.UserAgreement;
import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.exception.EmailAlreadyUsedException;
import cloud.anzaanza.antiagingdna.exception.SignUpNotAllowedException;
import cloud.anzaanza.antiagingdna.repository.DnaInfoRepository;
import cloud.anzaanza.antiagingdna.repository.UserAgreementRepository;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "nosleep@gmail.com";
    private static final String RAW_PASSWORD = "password1234";

    @Mock private UserRepository userRepository;
    @Mock private UserAgreementRepository userAgreementRepository;
    @Mock private DnaInfoRepository dnaInfoRepository;
    @Mock private ScoringService scoringService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                userAgreementRepository,
                dnaInfoRepository,
                passwordEncoder,
                scoringService,
                clock);
    }

    // ── 회원가입 ─────────────────────────────────────────────────

    @Test
    void 가입하면_비밀번호가_해시로_저장된다() {
        givenSaveReturnsArgument();

        authService.signUp(signUpRequest(allAgreed()));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword())
                .describedAs("평문이 그대로 들어가면 안 된다")
                .isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getValue().getPassword())).isTrue();
    }

    @Test
    void 가입하면_초기진단과_약관동의가_함께_저장된다() {
        givenSaveReturnsArgument();

        authService.signUp(signUpRequest(allAgreed()));

        verify(dnaInfoRepository).save(any(DnaInfo.class));
        verify(userAgreementRepository, times(AgreementType.values().length))
                .save(any(UserAgreement.class));
    }

    /**
     * 가입 직후 메인 화면이 "점수 없음"으로 뜨면 안 된다. 일지가 0건이어도 온보딩 baseline
     * 만으로 day-0 점수가 나온다 (기획 §A).
     */
    @Test
    void 가입하면_그날의_day0_점수가_만들어진다() {
        givenSaveReturnsArgument();
        given(dnaInfoRepository.save(any(DnaInfo.class))).willAnswer(call -> call.getArgument(0));

        authService.signUp(signUpRequest(allAgreed()));

        verify(scoringService).recalculate(any(DnaInfo.class), eq(LocalDate.of(2026, 8, 10)));
    }

    /** "동의하지 않음"과 "묻지 않음"은 다른 사실이라, 거절한 항목도 행으로 남아야 한다 */
    @Test
    void 선택_약관을_거절해도_가입되고_거절_사실이_남는다() {
        givenSaveReturnsArgument();
        Map<AgreementType, Boolean> agreements = allAgreed();
        agreements.put(AgreementType.MARKETING, false);

        authService.signUp(signUpRequest(agreements));

        ArgumentCaptor<UserAgreement> saved = ArgumentCaptor.forClass(UserAgreement.class);
        verify(userAgreementRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .filteredOn(a -> a.getAgreementType() == AgreementType.MARKETING)
                .singleElement()
                .satisfies(a -> assertThat(a.isAgreed()).isFalse());
    }

    @Test
    void 이미_가입된_이메일이면_거부한다() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.signUp(signUpRequest(allAgreed())))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void 필수_약관에_동의하지_않으면_거부한다() {
        Map<AgreementType, Boolean> agreements = allAgreed();
        agreements.put(AgreementType.PRIVACY_SENSITIVE, false);

        assertThatThrownBy(() -> authService.signUp(signUpRequest(agreements)))
                .isInstanceOf(SignUpNotAllowedException.class)
                .hasMessageContaining("PRIVACY_SENSITIVE");

        verify(userRepository, never()).save(any());
    }

    @Test
    void 필수_약관_항목이_아예_빠져도_거부한다() {
        Map<AgreementType, Boolean> agreements = allAgreed();
        agreements.remove(AgreementType.TERMS_OF_SERVICE);

        assertThatThrownBy(() -> authService.signUp(signUpRequest(agreements)))
                .isInstanceOf(SignUpNotAllowedException.class)
                .hasMessageContaining("TERMS_OF_SERVICE");
    }

    @Test
    void 만_14세_미만이면_거부한다() {
        int tooYoung = LocalDate.now(clock).getYear() - 13;

        assertThatThrownBy(() -> authService.signUp(signUpRequest(allAgreed(), tooYoung)))
                .isInstanceOf(SignUpNotAllowedException.class)
                .hasMessageContaining("14세");
    }

    @Test
    void 미래의_출생연도는_거부한다() {
        int future = LocalDate.now(clock).getYear() + 1;

        assertThatThrownBy(() -> authService.signUp(signUpRequest(allAgreed(), future)))
                .isInstanceOf(SignUpNotAllowedException.class);
    }

    // ── 로그인 ───────────────────────────────────────────────────

    @Test
    void 올바른_자격증명이면_사용자를_돌려준다() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(storedUser()));

        assertThat(authService.authenticate(EMAIL, RAW_PASSWORD).getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void 비밀번호가_틀리면_거부한다() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(storedUser()));

        assertThatThrownBy(() -> authService.authenticate(EMAIL, "wrong-password-1"))
                .isInstanceOf(BadCredentialsException.class);
    }

    /** 없는 계정과 틀린 비밀번호의 응답이 구분되면 로그인 화면이 가입 여부 조회 API 가 된다 */
    @Test
    void 없는_계정도_같은_메시지로_거부한다() {
        when(userRepository.findByEmail("nobody@gmail.com")).thenReturn(Optional.empty());

        String unknownAccount = catchMessage(() -> authService.authenticate("nobody@gmail.com", RAW_PASSWORD));

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(storedUser()));
        String wrongPassword = catchMessage(() -> authService.authenticate(EMAIL, "wrong-password-1"));

        assertThat(unknownAccount).isEqualTo(wrongPassword);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private void givenSaveReturnsArgument() {
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    private User storedUser() {
        return User.builder()
                .id("user-1")
                .email(EMAIL)
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .nickname("안자안자")
                .birthYear(2002)
                .build();
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("예외가 발생해야 한다");
        } catch (BadCredentialsException e) {
            return e.getMessage();
        }
    }

    private static Map<AgreementType, Boolean> allAgreed() {
        Map<AgreementType, Boolean> agreements = new EnumMap<>(AgreementType.class);
        for (AgreementType type : AgreementType.values()) {
            agreements.put(type, true);
        }
        return agreements;
    }

    private static SignUpRequest signUpRequest(Map<AgreementType, Boolean> agreements) {
        return signUpRequest(agreements, 2002);
    }

    private static SignUpRequest signUpRequest(Map<AgreementType, Boolean> agreements, int birthYear) {
        return new SignUpRequest(
                EMAIL,
                RAW_PASSWORD,
                "안자안자",
                birthYear,
                diagnosis(),
                new LinkedHashMap<>(agreements));
    }

    private static DiagnosisRequest diagnosis() {
        return new DiagnosisRequest(
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
                null);
    }
}
