package cloud.anzaanza.antiagingdna.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "오늘"의 기준.
 *
 * <p>{@code LocalDate.now()} 를 직접 부르면 날짜 경계 동작을 테스트할 수 없고, 컨테이너의
 * 타임존(UTC)에 따라 한국 사용자의 자정이 9시간 어긋난다. 일지가 하루 1건이고 점수가 날짜
 * 단위라 이 경계는 도메인 규칙이다.
 *
 * <p><b>날짜와 시각을 나눠 쓴다</b> — 사용자에게 보이는 <b>달력 하루</b>({@code log_date},
 * {@code score_date}, 만 14세 판정)는 이 {@code Clock} 을 따르고, 기계 시각
 * ({@code created_at} 등 감사 컬럼, {@code agreed_at})은 시스템 시간대를 그대로 쓴다.
 * 감사 컬럼은 JPA 가 시스템 시간대로 채우므로, 같은 행의 형제 컬럼만 서비스 시간대로 바꾸면
 * 오히려 어긋난다.
 *
 * <p>이 구분을 어기면 CI(UTC)에서만 깨지는 테스트가 나온다 — 실제로 그렇게 한 번 깨졌다.
 */
@Configuration
public class TimeConfig {

    /** 서비스 대상이 국내 사용자라 KST 고정이다. 다국가로 넓히면 사용자별 타임존이 필요해진다 */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SERVICE_ZONE);
    }
}
