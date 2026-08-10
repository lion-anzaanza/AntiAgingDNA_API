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
