package cloud.anzaanza.antiagingdna.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 의 Authorize 버튼에 Bearer 토큰을 넣을 수 있게 한다. 이게 없으면 인증이 필요한
 * 엔드포인트를 문서에서 그대로 호출할 수 없다.
 *
 * <p>{@code info.title}/{@code version} 도 여기서 채운다 — 안 채우면 springdoc 기본값
 * "OpenAPI definition"/"v0" 로 나간다(FE backend-backlog.md #16).
 */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "AntiAgingDNA API",
                        version = "v0",
                        description = "LifeDNA — 초기 진단·오늘의 일지·종합점수 산출 백엔드"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {}
