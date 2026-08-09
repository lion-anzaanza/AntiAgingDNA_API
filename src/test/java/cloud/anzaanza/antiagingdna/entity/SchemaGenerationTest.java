package cloud.anzaanza.antiagingdna.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.SoftAssertions;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.DelayedDropRegistryNotAvailableImpl;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Flyway 마이그레이션과 엔티티 매핑이 어긋나지 않는지 검증한다.
 *
 * <p>런타임에는 {@code spring.jpa.hibernate.ddl-auto=validate} 가 부팅 시점에 같은 일을
 * 한다. 다만 그건 <b>실제 MySQL 이 붙어야</b> 동작하는데 CI 에는 DB 가 없다. 그래서 이
 * 테스트가 DB 없이 같은 대조를 수행하는 사전 점검 역할을 한다 — 매핑을 고치고 마이그레이션을
 * 안 쓰면 배포 시점이 아니라 여기서 깨진다.
 *
 * <p>기대 컬럼 목록을 이 파일에 손으로 적어두지 않는다. <b>Flyway 마이그레이션에서 파싱</b>해
 * 비교하므로 관리해야 할 사본이 늘지 않는다.
 *
 * <p>Docker 를 쓸 수 있게 되면 Testcontainers + 실제 MySQL 로 대체하는 것이 정석이다.
 */
class SchemaGenerationTest {

    private static final Class<?>[] ENTITIES = {
        User.class, UserAgreement.class, DnaInfo.class, Diary.class, DailyScore.class
    };

    private static final Path MIGRATION =
            Path.of("src", "main", "resources", "db", "migration", "V1__init.sql");

    private static String entityDdl;
    private static String migrationDdl;

    @BeforeAll
    static void generateSchema() throws IOException {
        Path target = Path.of("build", "generated-schema.sql");
        Files.createDirectories(target.getParent());
        Files.deleteIfExists(target);

        // application.properties 의 hibernate.* 설정과 동일하게 유지할 것
        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        settings.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", target.toString());

        StandardServiceRegistry registry =
                new StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (Class<?> entity : ENTITIES) {
                sources.addAnnotatedClass(entity);
            }
            Metadata metadata = sources.buildMetadata();
            SchemaManagementToolCoordinator.process(
                    metadata, registry, settings, DelayedDropRegistryNotAvailableImpl.INSTANCE);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
        entityDdl = Files.readString(target).toLowerCase();
        migrationDdl = stripComments(Files.readString(MIGRATION).toLowerCase());
    }

    @Test
    void 마이그레이션의_테이블과_컬럼이_엔티티_매핑과_일치한다() {
        Map<String, List<String>> fromEntities = tablesOf(entityDdl);
        Map<String, List<String>> fromMigration = tablesOf(migrationDdl);

        assertThat(fromMigration.keySet())
                .describedAs("V1__init.sql 의 테이블 목록")
                .containsExactlyInAnyOrderElementsOf(fromEntities.keySet());

        SoftAssertions softly = new SoftAssertions();
        fromEntities.forEach((table, columns) ->
                softly.assertThat(fromMigration.get(table))
                        .describedAs("%s 컬럼 — 엔티티와 V1__init.sql 불일치", table)
                        .containsExactlyInAnyOrderElementsOf(columns));
        softly.assertAll();
    }

    @Test
    void 마이그레이션의_제약이_엔티티_매핑과_일치한다() {
        assertThat(constraintsOf(migrationDdl))
                .containsExactlyInAnyOrderElementsOf(constraintsOf(entityDdl));

        // 제약 이름을 지정하지 않으면 Hibernate 가 FKm8b22a1dwv935sexcc3pwq31b 같은 해시를
        // 붙인다. 매핑이 바뀌면 이름도 바뀌므로 운영 중 제약 위반 로그로 관계를 특정할 수 없다.
        assertThat(constraintsOf(entityDdl))
                .describedAs("이름 없는(자동 생성) 제약")
                .allMatch(n -> n.startsWith("fk_") || n.startsWith("uk_"));

        // dna_info 는 user 와 PK 를 공유한다(@MapsId) — 별도 FK 컬럼이 생기면 안 된다
        assertThat(tablesOf(entityDdl).get("dna_info")).doesNotContain("user_id");
    }

    /**
     * Flyway 가 실제로 <b>실행될 수 있는 상태</b>인지 본다.
     *
     * <p>2026-08-10 에 이걸 놓쳐서 배포가 크래시 루프에 빠졌다. `org.flywaydb:flyway-core` 만
     * 의존성에 넣었는데, Spring Boot 4 는 자동설정을 모듈별로 분리해서 <b>라이브러리는 올라가도
     * 마이그레이션이 돌지 않는다</b>. Flyway 가 침묵한 채 `ddl-auto=validate` 만 동작해
     * `missing table [daily_score]` 로 부팅에 실패했다.
     *
     * <p>이 실패는 DB 없이는 기존 테스트로 잡히지 않았다 — 매핑 검증은 스프링을 띄우지 않고,
     * `contextLoads` 는 DB 가 없어 어차피 실패하기 때문이다. 그래서 클래스패스 수준에서 본다.
     */
    @Test
    void Flyway_자동설정과_마이그레이션이_클래스패스에_있다() {
        assertThat(
                        Thread.currentThread()
                                .getContextClassLoader()
                                .getResource(
                                        "org/springframework/boot/flyway/autoconfigure/FlywayAutoConfiguration.class"))
                .describedAs(
                        "spring-boot-flyway 모듈이 없다. flyway-core 만으로는 마이그레이션이 실행되지 않는다")
                .isNotNull();

        assertThat(MIGRATION)
                .describedAs("Flyway 기본 위치(classpath:db/migration)의 마이그레이션")
                .exists();
        assertThat(MIGRATION.getFileName().toString())
                .describedAs("Flyway 네이밍 규칙 V<버전>__<설명>.sql")
                .matches("V\\d+(\\.\\d+)*__.+\\.sql");
    }

    @Test
    void enum_은_네이티브_ENUM_이_아니라_VARCHAR_로_매핑된다() {
        // MySQLDialect 기본값은 enum('A','B',…) 라 선택지 목록이 컬럼 정의에 박힌다.
        // 그러면 상수를 하나 추가할 때마다 ALTER 마이그레이션이 필요하다.
        // 모든 enum 필드의 @JdbcTypeCode(SqlTypes.VARCHAR) 가 이를 막는다.
        assertThat(entityDdl).doesNotContain(" enum (");
        assertThat(entityDdl)
                .contains("sleep_type varchar(32)")
                .contains("drink_frequency varchar(32)")
                .contains("agreement_type varchar(32)");
    }

    // ── 파싱 유틸 ─────────────────────────────────────────────────

    /** {@code create table X (...)} 전부를 테이블명 → 컬럼명 목록으로 */
    private static Map<String, List<String>> tablesOf(String sql) {
        Map<String, List<String>> tables = new LinkedHashMap<>();
        Matcher m = Pattern.compile("create table (\\w+)\\s*\\((.*?)\\)\\s*engine", Pattern.DOTALL).matcher(sql);
        while (m.find()) {
            tables.put(m.group(1), columnsIn(m.group(2)));
        }
        assertThat(tables).describedAs("파싱된 테이블").isNotEmpty();
        return tables;
    }

    /** 괄호 깊이를 세어 타입 안의 콤마(decimal(5,2))를 무시하고 컬럼명만 뽑는다 */
    private static List<String> columnsIn(String body) {
        List<String> columns = new ArrayList<>();
        int depth = 0;
        StringBuilder segment = new StringBuilder();
        for (char c : (body + ",").toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                String s = segment.toString().trim();
                if (!s.isEmpty() && !s.startsWith("primary key")) {
                    columns.add(s.split("\\s+")[0]);
                }
                segment.setLength(0);
            } else {
                segment.append(c);
            }
        }
        return columns;
    }

    private static List<String> constraintsOf(String sql) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("add constraint (\\S+) (?:foreign key|unique)").matcher(sql);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /** {@code comment '...'} 과 {@code --} 주석 제거 — 파싱이 주석 내용에 걸리지 않게 */
    private static String stripComments(String sql) {
        return sql.replaceAll("comment\\s*'[^']*'", "").replaceAll("(?m)--.*$", "");
    }
}
