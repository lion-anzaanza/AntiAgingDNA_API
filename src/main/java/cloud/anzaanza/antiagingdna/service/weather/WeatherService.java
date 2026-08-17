package cloud.anzaanza.antiagingdna.service.weather;

import cloud.anzaanza.antiagingdna.config.WeatherProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 위경도로 현재 날씨를 조회한다 — FE backend-backlog.md #12.
 *
 * <p>날씨는 일지의 부가 정보다. 키 미설정·외부 API 장애·응답 파싱 실패 어느 경우든
 * {@code null}(결측)을 돌려줄 뿐 예외를 던지지 않는다 — 날씨 조회 실패로 일지 저장 자체가
 * 막히면 안 된다({@link cloud.anzaanza.antiagingdna.service.DiaryService#save}).
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    /** +900 이상·-900 이하는 관측장비 결측을 뜻하는 기상청 sentinel 값이다(활용가이드 §2) — 실제 값이 아니다 */
    private static final BigDecimal MISSING_THRESHOLD = new BigDecimal("900");

    private final KmaWeatherClient client;
    private final WeatherProperties properties;
    private final Clock clock;

    WeatherService(KmaWeatherClient client, WeatherProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    /** 키가 없으면 조용히 결측을 돌려준다 — 미설정은 정상 상태이지 오류가 아니다 */
    public WeatherSnapshot lookup(double latitude, double longitude) {
        if (!properties.isConfigured()) {
            return null;
        }
        try {
            GridCoordinate grid = WeatherGridConverter.of(latitude, longitude);
            LocalDateTime now = LocalDateTime.now(clock);

            Map<String, String> ncst =
                    client.getUltraSrtNcst(KmaBaseTime.ultraShortTermObservation(now), grid);
            Map<String, String> fcst =
                    client.getUltraSrtFcst(KmaBaseTime.ultraShortTermForecast(now), grid);

            BigDecimal temperature = parseDecimal(ncst.get("T1H"));
            Integer humidity = parseInt(ncst.get("REH"));
            WeatherCondition condition = WeatherCondition.of(parseInt(fcst.get("SKY")), parseInt(fcst.get("PTY")));

            if (temperature == null && humidity == null && condition == null) {
                return null;
            }
            return new WeatherSnapshot(temperature, humidity, condition);
        } catch (WeatherLookupException e) {
            // 외부 API 가 실패로 답한 경우 — 흔히 있는 일이라 info 로만 남긴다
            log.info("날씨 조회 실패(기상청 API) — 결측으로 처리: {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            // 파싱 오류 등 예상 못한 예외 — 버그일 수 있어 warn 으로 구분해 남긴다
            log.warn("날씨 조회 중 예상치 못한 오류 — 결측으로 처리하고 일지 저장은 계속 진행한다", e);
            return null;
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null) {
            return null;
        }
        BigDecimal parsed = new BigDecimal(value);
        return parsed.abs().compareTo(MISSING_THRESHOLD) >= 0 ? null : parsed;
    }

    private static Integer parseInt(String value) {
        BigDecimal decimal = parseDecimal(value);
        return decimal == null ? null : decimal.intValue();
    }
}
