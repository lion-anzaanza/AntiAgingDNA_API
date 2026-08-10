package cloud.anzaanza.antiagingdna.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 액세스 토큰 서명 설정.
 *
 * <p>대칭키(HS256)를 쓴다. 토큰을 발급하는 주체와 검증하는 주체가 같은 프로세스라 공개키를
 * 배포할 상대가 없고, 비대칭키는 키 쌍 관리·회전 절차만 늘린다.
 *
 * @param secret 서명 키. 환경변수 {@code JWT_SECRET} 으로 주입한다 — DB 자격증명과 마찬가지로
 *     기본값을 두지 않는다. 값이 없으면 부팅이 실패하고, 그게 조용히 약한 키로 뜨는 것보다 낫다.
 * @param accessTokenTtl 액세스 토큰 유효기간
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) {

    /** HS256 은 키가 해시 출력 길이(256비트) 이상이어야 한다 — RFC 7518 §3.2 */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 은 최소 " + MIN_SECRET_BYTES + "바이트여야 한다 (HS256, RFC 7518 §3.2)");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalStateException("jwt.access-token-ttl 은 양수여야 한다");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
