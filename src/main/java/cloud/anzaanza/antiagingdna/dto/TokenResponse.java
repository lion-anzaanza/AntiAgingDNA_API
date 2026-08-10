package cloud.anzaanza.antiagingdna.dto;

/**
 * 가입·로그인 응답.
 *
 * <p>필드 이름은 OAuth 2.0 토큰 응답(RFC 6749 §5.1)을 따른다 — 클라이언트 라이브러리들이
 * 이미 아는 형태라 새로 약속할 것이 없다. 다만 자체 발급 토큰이라 refresh_token 은 없다.
 *
 * @param expiresIn 만료까지 남은 초
 */
public record TokenResponse(
        String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static final String BEARER = "Bearer";

    public static TokenResponse bearer(String accessToken, long expiresIn, UserResponse user) {
        return new TokenResponse(accessToken, BEARER, expiresIn, user);
    }
}
