package cloud.anzaanza.antiagingdna.controller;

import cloud.anzaanza.antiagingdna.exception.DiagnosisNotFoundException;
import cloud.anzaanza.antiagingdna.exception.DiaryNotFoundException;
import cloud.anzaanza.antiagingdna.exception.EmailAlreadyUsedException;
import cloud.anzaanza.antiagingdna.exception.LoginIdAlreadyUsedException;
import cloud.anzaanza.antiagingdna.exception.SignUpNotAllowedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 오류 응답을 RFC 9457 {@code application/problem+json} 으로 통일한다.
 *
 * <p>응답 형식을 새로 정의하지 않는다 — {@code ProblemDetail} 은 스프링이 이미 프레임워크
 * 예외에 쓰는 형식이라, 도메인 예외만 같은 형식에 얹으면 클라이언트가 보는 오류 본문이 한
 * 종류로 유지된다.
 *
 * <p>{@link ResponseEntityExceptionHandler} 를 상속한다. 이 타입의 빈이 있으면 부트의
 * {@code ProblemDetailsExceptionHandler} 가 물러나므로(@ConditionalOnMissingBean), 400·404·405
 * 같은 프레임워크 예외까지 여기 한 곳에서 다룬다. 상속하지 않고 별도 advice 로 두면 부트 쪽이
 * {@code @Order(0)} 으로 앞서서 {@code MethodArgumentNotValidException} 을 가로챈다.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException e) {
        return problem(HttpStatus.CONFLICT, "이메일 중복", e.getMessage());
    }

    @ExceptionHandler(LoginIdAlreadyUsedException.class)
    public ProblemDetail handleLoginIdAlreadyUsed(LoginIdAlreadyUsedException e) {
        return problem(HttpStatus.CONFLICT, "아이디 중복", e.getMessage());
    }

    @ExceptionHandler(DiaryNotFoundException.class)
    public ProblemDetail handleDiaryNotFound(DiaryNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "일지 없음", e.getMessage());
    }

    @ExceptionHandler(DiagnosisNotFoundException.class)
    public ProblemDetail handleDiagnosisNotFound(DiagnosisNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "초기 진단 없음", e.getMessage());
    }

    @ExceptionHandler(SignUpNotAllowedException.class)
    public ProblemDetail handleSignUpNotAllowed(SignUpNotAllowedException e) {
        return problem(HttpStatus.BAD_REQUEST, "가입 조건 미충족", e.getMessage());
    }

    /**
     * 자격증명 실패는 401 이다. 어느 쪽이 틀렸는지는 {@code AuthService} 가 이미 뭉개서
     * 던지므로 여기서 더 노출할 정보가 없다.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "인증 실패", e.getMessage());
    }

    /**
     * DB 제약 위반이 그대로 새면 스택트레이스가 담긴 500 이 나간다. 이 프로젝트에 아직
     * 명시적인 낙관적 락이 없어, 동시 요청이 FK/유니크 제약과 충돌하는 경로(예: 회원탈퇴
     * 도중 다른 요청이 같은 계정으로 쓰기)를 여기서 걸러 일관된 형식으로 낸다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "요청을 처리할 수 없음", "다른 요청과 충돌했습니다. 다시 시도해주세요");
    }

    /**
     * {@code @RequestParam}/{@code @PathVariable} 제약(예: {@code checkEmail(@Email ...)})
     * 위반은 {@link MethodArgumentNotValidException} 이 아니라 이걸로 온다 — 컨트롤러가
     * {@code @Validated} 로 감싼 메서드 인자에 붙는 경로라 바인딩 단계가 다르다. 형식은
     * {@link #handleMethodArgumentNotValid} 와 맞춘다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.putIfAbsent(field, violation.getMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "입력값 오류", "요청 값이 올바르지 않습니다");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** 필드 단위 위반은 {@code errors} 확장 속성에 담는다 — 화면이 입력칸 옆에 붙일 수 있도록 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "입력값 오류", "요청 값이 올바르지 않습니다");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
