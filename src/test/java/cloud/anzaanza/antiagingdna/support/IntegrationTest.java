package cloud.anzaanza.antiagingdna.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 MySQL 을 띄우고 애플리케이션을 <b>진짜로 부팅</b>해서 검증하는 테스트의 기반.
 *
 * <p>다른 테스트들이 못 보는 것을 본다:
 * <ul>
 *   <li>Flyway 마이그레이션이 실제로 적용되는가 — 2026-08-10 에 이게 빠져 배포가 크래시
 *       루프에 빠졌다. 클래스패스 검사로 우회했지만 그건 "실행될 수 있는 상태"까지만 본다.</li>
 *   <li>{@code ddl-auto=validate} 가 통과하는가 — 엔티티와 테이블의 실제 대조.</li>
 *   <li>스프링 컨텍스트 전체가 뜨는가 — 빈 설정 오류는 여기서만 드러난다.</li>
 * </ul>
 *
 * <p><b>Docker 가 없으면 이 계층의 테스트는 통째로 비활성된다</b>
 * ({@code disabledWithoutDocker}). 실패가 아니라 건너뛰기다 — 개발 머신에 Docker 가 없다고
 * {@code ./gradlew test} 가 빨개지면, 진짜 실패가 섞여도 아무도 눈치채지 못하게 된다.
 * CI 는 Docker 가 항상 있으므로 거기서는 반드시 실행된다({@code .github/workflows/ci.yml}).
 *
 * <p>컨테이너는 {@code static} 이라 이 기반을 공유하는 테스트 클래스들이 하나를 나눠 쓴다.
 * 매 클래스마다 MySQL 을 새로 띄우면 CI 시간이 분 단위로 늘어난다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class IntegrationTest {

    /** 운영과 같은 메이저 버전. 다르면 여기서 통과한 스키마가 운영에서 깨질 수 있다 */
    private static final String MYSQL_IMAGE = "mysql:8.0";

    @SuppressWarnings("resource") // Testcontainers 가 JVM 종료 시 정리한다
    private static final MySQLContainer MYSQL =
            new MySQLContainer(MYSQL_IMAGE).withDatabaseName("antiagingdna");

    static {
        MYSQL.start();
    }

    /**
     * 운영과 동일하게 환경변수 자리(${DB_URL} 등)를 채운다. 애플리케이션 설정은 손대지 않으므로
     * {@code application.properties} 가 실제로 쓰는 값들이 그대로 검증된다.
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // 운영에서는 GitHub Secrets 로 주입된다. 값 자체는 아무거나 되지만 32바이트 이상이어야 한다
        registry.add("jwt.secret", () -> "통합테스트-서명키-32바이트-이상이어야-한다-0123456789");
    }
}
