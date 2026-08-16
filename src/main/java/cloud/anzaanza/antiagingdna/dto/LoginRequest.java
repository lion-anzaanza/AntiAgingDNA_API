package cloud.anzaanza.antiagingdna.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * <p>식별자는 아이디다(기획 결정, 2026-08-16 — FE backend-backlog.md #2). 목업 {@code
 * 00_LOGIN_OR_REGISTER} 의 "아이디를 입력하세요" 라벨이 맞다.
 */
public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {}
