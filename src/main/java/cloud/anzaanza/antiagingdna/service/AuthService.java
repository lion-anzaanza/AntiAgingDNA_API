package cloud.anzaanza.antiagingdna.service;

import cloud.anzaanza.antiagingdna.dto.SignUpRequest;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.UserAgreement;
import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import cloud.anzaanza.antiagingdna.exception.EmailAlreadyUsedException;
import cloud.anzaanza.antiagingdna.exception.SignUpNotAllowedException;
import cloud.anzaanza.antiagingdna.repository.DnaInfoRepository;
import cloud.anzaanza.antiagingdna.repository.UserAgreementRepository;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입과 자격증명 확인. 토큰 발급은 {@link TokenService} 가 한다. */
@Service
public class AuthService {

    /**
     * 만 14세 미만은 가입시키지 않는다 — STEP 4 의 "[필수] 만 14세 이상입니다" 체크와 같은 규칙을
     * 서버에서도 본다. 다만 생일이 없고 출생연도만 있어 <b>연 나이</b>로 판정한다. 생일이 지나지
     * 않은 만 13세가 통과할 수 있는데, 그쪽 오차를 택했다. 반대로 잡으면 실제로 자격이 있는
     * 사람이 막힌다.
     */
    private static final int MINIMUM_AGE = 14;

    /**
     * 존재하지 않는 계정으로 로그인해도 해시 비교 비용을 똑같이 치르게 하는 더미 값.
     * 응답 시간 차이로 가입 여부를 캐내는 것을 막는다 —
     * Spring Security {@code DaoAuthenticationProvider} 가 쓰는 것과 같은 수법이다.
     */
    private static final String DUMMY_PASSWORD = "userNotFoundPassword";

    private final UserRepository userRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final DnaInfoRepository dnaInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            UserAgreementRepository userAgreementRepository,
            DnaInfoRepository dnaInfoRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userAgreementRepository = userAgreementRepository;
        this.dnaInfoRepository = dnaInfoRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    /**
     * 목업 STEP 2·3·4 를 한 트랜잭션으로 처리한다. 계정만 남고 진단이 없는 상태, 진단만 남고
     * 약관 동의가 없는 상태가 생기지 않는다.
     */
    @Transactional
    public User signUp(SignUpRequest request) {
        verifyAge(request.birthYear());
        verifyRequiredAgreements(request.agreements());
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }

        User user = userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .birthYear(request.birthYear())
                .build());

        dnaInfoRepository.save(request.diagnosis().toEntity(user));
        saveAgreements(user, request.agreements());
        return user;
    }

    /**
     * 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분해서 알려주지 않는다 — 구분하면 로그인
     * 화면이 가입 여부 조회 API 가 된다.
     */
    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
        }
        return user;
    }

    /**
     * 토큰의 {@code sub} 로 계정을 찾는다. 서명이 유효해도 계정이 지워졌으면 그 토큰은 더 이상
     * 자격증명이 아니므로 401 로 떨어뜨린다.
     */
    @Transactional(readOnly = true)
    public User findById(String userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BadCredentialsException("존재하지 않는 계정입니다"));
    }

    private void verifyAge(int birthYear) {
        int currentYear = Year.now().getValue();
        if (birthYear > currentYear) {
            throw new SignUpNotAllowedException("출생연도가 올바르지 않습니다");
        }
        if (currentYear - birthYear < MINIMUM_AGE) {
            throw new SignUpNotAllowedException("만 " + MINIMUM_AGE + "세 미만은 가입할 수 없습니다");
        }
    }

    private void verifyRequiredAgreements(Map<AgreementType, Boolean> agreements) {
        List<AgreementType> missing = AgreementType.required().stream()
                .filter(type -> !Boolean.TRUE.equals(agreements.get(type)))
                .toList();
        if (!missing.isEmpty()) {
            throw new SignUpNotAllowedException("필수 약관에 동의해야 합니다: " + missing);
        }
    }

    /**
     * 동의하지 않은 항목도 행으로 남긴다. "동의하지 않음"과 "묻지 않음"은 다른 사실이고,
     * 나중에 마케팅 동의를 다시 물어야 하는지 판단하려면 그 구분이 필요하다.
     */
    private void saveAgreements(User user, Map<AgreementType, Boolean> agreements) {
        LocalDateTime now = LocalDateTime.now();
        agreements.forEach((type, agreed) -> userAgreementRepository.save(UserAgreement.builder()
                .user(user)
                .agreementType(type)
                .agreed(Boolean.TRUE.equals(agreed))
                .agreedAt(now)
                .build()));
    }
}
