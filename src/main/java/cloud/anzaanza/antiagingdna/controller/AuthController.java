package cloud.anzaanza.antiagingdna.controller;

import cloud.anzaanza.antiagingdna.dto.AvailabilityResponse;
import cloud.anzaanza.antiagingdna.dto.LoginRequest;
import cloud.anzaanza.antiagingdna.dto.SignUpRequest;
import cloud.anzaanza.antiagingdna.dto.TokenResponse;
import cloud.anzaanza.antiagingdna.dto.UserResponse;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.service.AuthService;
import cloud.anzaanza.antiagingdna.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    /**
     * 회원가입. 목업 STEP 2·3·4 를 한 번에 받는다.
     *
     * <p>가입 직후 토큰을 함께 준다 — 마지막 버튼("가입하고 내 LifeDAN 만들기")을 누르면 바로
     * 앱으로 들어가는 흐름이라, 여기서 다시 로그인시킬 이유가 없다.
     */
    @Operation(
            summary = "회원가입",
            description = "목업 STEP 2(개인정보)·3(초기 진단)·4(약관 동의)를 한 번에 받는다. "
                    + "성공 시 계정이 즉시 활성화되고 로그인 토큰이 함께 발급된다(별도 이메일/본인 인증 없음).")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return issueFor(authService.signUp(request));
    }

    @Operation(summary = "로그인", description = "로그인 식별자는 이메일이 아니라 아이디(loginId)다.")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return issueFor(authService.authenticate(request.loginId(), request.password()));
    }

    /** 토큰이 누구 것인지 확인한다. 클라이언트가 앱 시작 시 세션 복원에 쓴다. */
    @Operation(summary = "내 계정 조회", description = "토큰의 sub 로 계정을 찾는다. 앱 시작 시 세션 복원용.")
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = authService.findById(jwt.getSubject());
        return UserResponse.from(user, authService.currentStreak(user.getId()));
    }

    /**
     * 회원탈퇴. 계정과 진단·일지·점수를 전부 지운다(개인정보보호법상 삭제 요청) — 소프트
     * 삭제가 아니라 실제 삭제이며 되돌릴 수 없다.
     */
    @Operation(
            summary = "회원탈퇴",
            description = "계정·초기 진단·일지·점수를 전부 삭제한다. 되돌릴 수 없다.")
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void withdraw(@AuthenticationPrincipal Jwt jwt) {
        authService.withdraw(jwt.getSubject());
    }

    /**
     * 가입 폼에서 미리 확인하는 용도. 인증 불필요.
     *
     * <p>가입 시(SignUpRequest)과 같은 형식 제약을 건다 — 안 그러면 형식이 잘못된 값도
     * "사용 가능"으로 나왔다가 실제 가입 제출에서 400 이 나는 모순이 생긴다.
     */
    @Operation(summary = "이메일 중복 확인", description = "가입 폼에서 미리 확인하는 용도. 인증 불필요.")
    @GetMapping("/check-email")
    public AvailabilityResponse checkEmail(
            @RequestParam @Email @Size(max = 255) String email) {
        return new AvailabilityResponse(!authService.emailExists(email));
    }

    /** 가입 폼에서 미리 확인하는 용도. 인증 불필요. 형식 제약은 {@link #checkEmail} 과 같은 이유 */
    @Operation(summary = "아이디 중복 확인", description = "가입 폼에서 미리 확인하는 용도. 인증 불필요.")
    @GetMapping("/check-login-id")
    public AvailabilityResponse checkLoginId(
            @RequestParam
                    @Size(min = SignUpRequest.LOGIN_ID_MIN, max = SignUpRequest.LOGIN_ID_MAX)
                    @Pattern(regexp = SignUpRequest.LOGIN_ID_PATTERN)
                    String loginId) {
        return new AvailabilityResponse(!authService.loginIdExists(loginId));
    }

    private TokenResponse issueFor(User user) {
        TokenService.IssuedToken token = tokenService.issue(user);
        UserResponse userResponse = UserResponse.from(user, authService.currentStreak(user.getId()));
        return TokenResponse.bearer(token.value(), token.expiresIn(), userResponse);
    }
}
