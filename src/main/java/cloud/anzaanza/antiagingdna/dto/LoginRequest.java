package cloud.anzaanza.antiagingdna.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * <p>목업 {@code 00_LOGIN_OR_REGISTER} 의 첫 필드 라벨은 "아이디"지만 가입 화면(STEP 2)에
 * 아이디 입력란이 없다. 식별자는 이메일이다 — 화면 라벨을 "이메일"로 고쳐야 한다.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
