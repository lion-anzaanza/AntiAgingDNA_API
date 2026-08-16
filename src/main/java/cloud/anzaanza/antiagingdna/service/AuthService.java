package cloud.anzaanza.antiagingdna.service;

import cloud.anzaanza.antiagingdna.dto.SignUpRequest;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.UserAgreement;
import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import cloud.anzaanza.antiagingdna.exception.EmailAlreadyUsedException;
import cloud.anzaanza.antiagingdna.exception.LoginIdAlreadyUsedException;
import cloud.anzaanza.antiagingdna.exception.SignUpNotAllowedException;
import cloud.anzaanza.antiagingdna.repository.DailyScoreRepository;
import cloud.anzaanza.antiagingdna.repository.DiaryRepository;
import cloud.anzaanza.antiagingdna.repository.DnaInfoRepository;
import cloud.anzaanza.antiagingdna.repository.UserAgreementRepository;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final DiaryRepository diaryRepository;
    private final DailyScoreRepository dailyScoreRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScoringService scoringService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            UserAgreementRepository userAgreementRepository,
            DnaInfoRepository dnaInfoRepository,
            DiaryRepository diaryRepository,
            DailyScoreRepository dailyScoreRepository,
            PasswordEncoder passwordEncoder,
            ScoringService scoringService,
            Clock clock) {
        this.userRepository = userRepository;
        this.userAgreementRepository = userAgreementRepository;
        this.dnaInfoRepository = dnaInfoRepository;
        this.diaryRepository = diaryRepository;
        this.dailyScoreRepository = dailyScoreRepository;
        this.passwordEncoder = passwordEncoder;
        this.scoringService = scoringService;
        this.clock = clock;
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
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new LoginIdAlreadyUsedException(request.loginId());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }

        User user = userRepository.save(User.builder()
                .loginId(request.loginId())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .birthYear(request.birthYear())
                .build());

        DnaInfo dna = dnaInfoRepository.save(request.diagnosis().toEntity(user));
        saveAgreements(user, request.agreements());

        // day-0 점수 — 일지가 0건이어도 온보딩 baseline 만으로 표시 점수가 나온다 (기획 §A).
        // 가입 직후 메인 화면이 "점수 없음"으로 뜨지 않게 하는 것이 이 한 줄의 목적이다.
        scoringService.recalculate(dna, LocalDate.now(clock));
        return user;
    }

    /**
     * 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분해서 알려주지 않는다 — 구분하면 로그인
     * 화면이 가입 여부 조회 API 가 된다.
     */
    @Transactional(readOnly = true)
    public User authenticate(String loginId, String rawPassword) {
        User user = userRepository.findByLoginId(loginId).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다");
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

    /**
     * 회원탈퇴 — 계정과 연관 데이터를 전부 지운다(개인정보보호법상 삭제 요청, FE
     * backend-backlog.md #24). 소프트 삭제가 아니라 실제 삭제다 — 이 프로젝트에 소프트
     * 삭제 인프라(deleted_at 등)가 없고, "탈퇴"의 통상적 의미가 그것이다.
     *
     * <p>FK 참조 방향의 역순으로 지운다: user_agreement/diary/daily_score(전부 user.id 를
     * FK 로 참조) → dna_info(user 와 PK 공유) → user. 순서를 바꾸면 FK 제약 위반으로 실패한다.
     */
    @Transactional
    public void withdraw(String userId) {
        findById(userId); // 존재하지 않는 계정이면 401 로 떨어진다(findById 의 기존 규약)
        userAgreementRepository.deleteByUserId(userId);
        diaryRepository.deleteByAuthorId(userId);
        dailyScoreRepository.deleteByUserId(userId);
        dnaInfoRepository.deleteById(userId);
        userRepository.deleteById(userId);
    }

    /** 가입 폼에서 미리 확인하는 용도(FE backend-backlog.md #14) — 인증 불필요, 공개 엔드포인트 */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /** 가입 폼에서 미리 확인하는 용도(FE backend-backlog.md #14) — 인증 불필요, 공개 엔드포인트 */
    @Transactional(readOnly = true)
    public boolean loginIdExists(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }

    private void verifyAge(int birthYear) {
        // Year.now() 는 JVM 기본 시간대를 따른다. 컨테이너는 UTC 라 연말·연초 9시간 동안
        // 한국 기준과 연도가 어긋난다 — 가입 가부를 가르는 판정이므로 서비스 시간대로 본다.
        int currentYear = LocalDate.now(clock).getYear();
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
        // 시각(timestamp)은 서비스 시간대가 아니라 시스템 시간대를 쓴다 — JPA 감사 컬럼
        // (created_at 등)이 그렇게 기록되므로 같은 행의 형제 컬럼끼리 어긋나지 않게 맞춘다.
        // 날짜(달력 하루)만 Clock 을 따른다. TimeConfig 참고.
        LocalDateTime now = LocalDateTime.now();
        agreements.forEach((type, agreed) -> userAgreementRepository.save(UserAgreement.builder()
                .user(user)
                .agreementType(type)
                .agreed(Boolean.TRUE.equals(agreed))
                .agreedAt(now)
                .build()));
    }
}
