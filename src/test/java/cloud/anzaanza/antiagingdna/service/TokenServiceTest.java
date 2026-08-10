package cloud.anzaanza.antiagingdna.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import cloud.anzaanza.antiagingdna.config.JwtProperties;
import cloud.anzaanza.antiagingdna.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 발급한 토큰을 운영과 같은 디코더로 되읽어, 서명·클레임·만료가 의도대로인지 본다. */
class TokenServiceTest {

    private static final String SECRET = "테스트용-서명키-32바이트-이상이어야-한다-0123456789";
    private static final Duration TTL = Duration.ofHours(24);

    private final JwtProperties properties = new JwtProperties(SECRET, TTL);
    private final TokenService tokenService =
            new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(properties.secretKey())), properties);
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

    private static User user(String id, String nickname) {
        return User.builder()
                .id(id)
                .email("nosleep@gmail.com")
                .password("{bcrypt}whatever")
                .nickname(nickname)
                .birthYear(2002)
                .build();
    }

    @Test
    void 발급한_토큰은_사용자_PK_를_sub_로_갖는다() {
        TokenService.IssuedToken issued = tokenService.issue(user("user-1", "안자안자"));

        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo("user-1");
        assertThat(jwt.getClaimAsString("nickname")).isEqualTo("안자안자");
        assertThat(issued.expiresIn()).isEqualTo(TTL.toSeconds());
        assertThat(jwt.getExpiresAt())
                .isCloseTo(Instant.now().plus(TTL), within(60, ChronoUnit.SECONDS));
    }

    /** 이메일이 아니라 PK 를 넣는 이유 — 이메일이 바뀌어도 발급된 토큰이 다른 사람을 가리키지 않는다 */
    @Test
    void 토큰에_비밀번호_해시나_이메일이_실리지_않는다() {
        Jwt jwt = decoder.decode(tokenService.issue(user("user-1", "안자안자")).value());

        assertThat(jwt.getClaims()).doesNotContainKeys("password", "email");
    }

    @Test
    void 다른_키로_서명된_토큰은_거부된다() {
        JwtProperties other = new JwtProperties("전혀-다른-서명키-32바이트-이상이어야-한다-98765", TTL);
        TokenService forged = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(other.secretKey())), other);

        String token = forged.issue(user("user-1", "안자안자")).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 짧은_시크릿은_부팅_시점에_거부된다() {
        assertThatThrownBy(() -> new JwtProperties("too-short", TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
