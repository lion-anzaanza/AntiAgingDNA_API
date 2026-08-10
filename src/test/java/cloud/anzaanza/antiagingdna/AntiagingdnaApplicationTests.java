package cloud.anzaanza.antiagingdna;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.anzaanza.antiagingdna.support.IntegrationTest;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 애플리케이션이 실제 MySQL 위에서 부팅되는지 본다.
 *
 * <p>예전에는 DB 가 없어 <b>항상 실패</b>했다. 늘 빨간 테스트는 진짜 실패를 가려주므로,
 * 실패를 방치하는 대신 실제 DB 를 붙였다. 2026-08-10 의 배포 크래시(Flyway 자동설정 누락 →
 * {@code missing table [daily_score]})가 바로 이 테스트가 잡았어야 할 사고다.
 */
class AntiagingdnaApplicationTests extends IntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    void contextLoads() {
        // 컨텍스트가 뜨는 것 자체가 검증이다:
        // Flyway 마이그레이션 적용 → ddl-auto=validate 통과 → 모든 빈 생성
    }

    @Test
    void Flyway_마이그레이션이_실제로_적용된다() throws Exception {
        assertThat(tableNames())
                .describedAs("Flyway 가 돌지 않으면 이 목록이 비어 있다")
                .contains("flyway_schema_history", "user", "user_agreement", "dna_info", "diary", "daily_score");
    }

    /**
     * {@code ddl-auto=validate} 는 <b>테이블이 있는지</b>만 보지 컬럼 타입까지 다 보지는 않는다.
     * enum 이 네이티브 {@code enum('A','B',…)} 로 만들어지면 상수를 추가할 때마다 ALTER 가
     * 필요해지므로, 실제 생성된 컬럼 타입을 확인한다.
     */
    @Test
    void enum_컬럼이_네이티브_ENUM_이_아니라_VARCHAR_로_생성된다() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery(
                    """
                    select count(*) from information_schema.columns
                     where table_schema = database() and data_type = 'enum'
                    """);
            rs.next();
            assertThat(rs.getInt(1)).describedAs("네이티브 enum 컬럼 수").isZero();
        }
    }

    private List<String> tableNames() throws Exception {
        List<String> tables = new ArrayList<>();
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery(
                    "select table_name from information_schema.tables where table_schema = database()");
            while (rs.next()) {
                tables.add(rs.getString(1).toLowerCase());
            }
        }
        return tables;
    }
}
