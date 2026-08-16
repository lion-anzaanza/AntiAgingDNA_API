package cloud.anzaanza.antiagingdna.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증 설정.
 *
 * <p>세션이 아니라 {@code Authorization: Bearer <JWT>} 를 쓴다. 클라이언트가 API 와 다른
 * 오리진이라 쿠키를 쓰면 {@code SameSite=None; Secure} 설정이 따라붙고, 컨테이너를 재배포할
 * 때마다 세션이 전부 날아간다.
 *
 * <p>토큰 검증 필터를 직접 만들지 않는다 — {@code oauth2ResourceServer(jwt)} 가 헤더 파싱,
 * 서명 검증, {@code exp} 확인, {@code SecurityContext} 주입을 전부 담당한다.
 */
@Configuration
// 부트가 자동설정으로도 켜주지만 명시한다. @WebMvcTest 슬라이스에는 시큐리티 자동설정이
// 포함되지 않아서, 이게 없으면 이 클래스를 import 해도 HttpSecurity 빈이 없어 뜨지 않는다.
@EnableWebSecurity
public class SecurityConfig {

    /** 인증 없이 열어두는 경로 */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/health",
        "/api/auth/signup",
        "/api/auth/login",
        "/api/auth/check-email",
        "/api/auth/check-login-id"
    };

    /** 시연용 문서. 노출 범위를 좁히는 건 별도 과제다 */
    private static final String[] DOC_ENDPOINTS = {
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CorsConfig 의 corsFilter 빈을 시큐리티 체인 안으로 끌어온다. 그래야 인증 실패
                // 응답(401)에도 CORS 헤더가 붙어 브라우저가 본문을 읽을 수 있다.
                .cors(Customizer.withDefaults())
                // CSRF 는 브라우저가 자동으로 실어 보내는 자격증명(쿠키)이 있을 때의 공격이다.
                // 토큰을 헤더에 직접 담으므로 해당 벡터가 없다.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(DOC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * BCrypt. 강도는 기본값(10)을 그대로 쓴다 — Spring Security 가 하드웨어 발전에 맞춰
     * 올려온 값이고, 여기서 임의로 손댈 근거가 없다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
