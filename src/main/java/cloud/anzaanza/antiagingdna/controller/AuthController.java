package cloud.anzaanza.antiagingdna.controller;

import cloud.anzaanza.antiagingdna.dto.LoginRequest;
import cloud.anzaanza.antiagingdna.dto.SignUpRequest;
import cloud.anzaanza.antiagingdna.dto.TokenResponse;
import cloud.anzaanza.antiagingdna.dto.UserResponse;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.service.AuthService;
import cloud.anzaanza.antiagingdna.service.TokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
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
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return issueFor(authService.signUp(request));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return issueFor(authService.authenticate(request.email(), request.password()));
    }

    /** 토큰이 누구 것인지 확인한다. 클라이언트가 앱 시작 시 세션 복원에 쓴다. */
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(authService.findById(jwt.getSubject()));
    }

    private TokenResponse issueFor(User user) {
        TokenService.IssuedToken token = tokenService.issue(user);
        return TokenResponse.bearer(token.value(), token.expiresIn(), UserResponse.from(user));
    }
}
