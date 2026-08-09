package cloud.anzaanza.antiagingdna.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@code @CreatedDate} / {@code @LastModifiedDate} 활성화.
 *
 * <p>메인 클래스가 아니라 별도 설정 클래스에 둔다 — {@code @EnableJpaAuditing} 을
 * {@code @SpringBootApplication} 에 붙이면 {@code @WebMvcTest} 류 슬라이스 테스트에서도
 * JPA 감사 인프라를 요구해 컨텍스트 로딩이 깨진다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
