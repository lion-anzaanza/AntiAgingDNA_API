package cloud.anzaanza.antiagingdna.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 의 Authorize 버튼에 Bearer 토큰을 넣을 수 있게 한다. 이게 없으면 인증이 필요한
 * 엔드포인트를 문서에서 그대로 호출할 수 없다.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {}
