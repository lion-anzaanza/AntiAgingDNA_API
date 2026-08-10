package cloud.anzaanza.antiagingdna.service;

import cloud.anzaanza.antiagingdna.config.JwtProperties;
import cloud.anzaanza.antiagingdna.entity.User;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * 액세스 토큰 발급. 검증은 {@code oauth2ResourceServer} 필터가 하므로 여기엔 없다.
 *
 * <p>{@code sub} 에 사용자 PK 를 넣는다. 이메일을 넣으면 이메일 변경 기능이 생기는 순간
 * 발급된 토큰이 전부 다른 사람을 가리킬 수 있다.
 */
@Service
public class TokenService {

    /** 토큰 발급자. 검증 측이 같은 프로세스라 URL 일 필요는 없다 */
    private static final String ISSUER = "antiagingdna";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(user.getId())
                // 화면 상단 인사말용. 토큰만으로 그릴 수 있으면 /me 왕복이 줄어든다.
                .claim("nickname", user.getNickname())
                .build();

        String value = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new IssuedToken(value, properties.accessTokenTtl().toSeconds());
    }

    /** @param expiresIn 만료까지 남은 초 */
    public record IssuedToken(String value, long expiresIn) {}
}
