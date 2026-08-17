package cloud.anzaanza.antiagingdna.service.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cloud.anzaanza.antiagingdna.config.WeatherProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 오케스트레이션만 본다 — 격자·base_time 산출은 각각의 단위 테스트가 담당. */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-17T09:50:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock private KmaWeatherClient client;

    @Test
    void 키가_없으면_클라이언트를_호출하지_않고_결측을_돌려준다() {
        WeatherService service = new WeatherService(client, new WeatherProperties(""), CLOCK);

        assertThat(service.lookup(37.5665, 126.9780)).isNull();
        verify(client, never()).getUltraSrtNcst(any(), any());
        verify(client, never()).getUltraSrtFcst(any(), any());
    }

    @Test
    void 실황과_예보를_합쳐_스냅샷을_만든다() {
        given(client.getUltraSrtNcst(any(), any())).willReturn(Map.of("T1H", "28.3", "REH", "55"));
        given(client.getUltraSrtFcst(any(), any())).willReturn(Map.of("SKY", "1", "PTY", "0"));
        WeatherService service = new WeatherService(client, new WeatherProperties("key"), CLOCK);

        WeatherSnapshot snapshot = service.lookup(37.5665, 126.9780);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.temperature()).isEqualByComparingTo(new BigDecimal("28.3"));
        assertThat(snapshot.humidity()).isEqualTo(55);
        assertThat(snapshot.condition()).isEqualTo(WeatherCondition.CLEAR);
    }

    @Test
    void 클라이언트가_예외를_던지면_일지_저장을_막지_않고_결측을_돌려준다() {
        given(client.getUltraSrtNcst(any(), any())).willThrow(new WeatherLookupException("서비스 연결실패"));
        WeatherService service = new WeatherService(client, new WeatherProperties("key"), CLOCK);

        assertThat(service.lookup(37.5665, 126.9780)).isNull();
    }

    @Test
    void 값이_전부_없으면_결측이다() {
        given(client.getUltraSrtNcst(any(), any())).willReturn(Map.of());
        given(client.getUltraSrtFcst(any(), any())).willReturn(Map.of());
        WeatherService service = new WeatherService(client, new WeatherProperties("key"), CLOCK);

        assertThat(service.lookup(37.5665, 126.9780)).isNull();
    }

    /** +900/-900 은 관측장비 결측을 뜻하는 기상청 sentinel 값이다(활용가이드 §2) — 실제 값으로 저장하면 안 된다 */
    @Test
    void 기상청_결측_sentinel_값은_결측으로_처리한다() {
        given(client.getUltraSrtNcst(any(), any())).willReturn(Map.of("T1H", "900", "REH", "55"));
        given(client.getUltraSrtFcst(any(), any())).willReturn(Map.of("SKY", "1", "PTY", "0"));
        WeatherService service = new WeatherService(client, new WeatherProperties("key"), CLOCK);

        WeatherSnapshot snapshot = service.lookup(37.5665, 126.9780);

        assertThat(snapshot.temperature()).isNull();
        assertThat(snapshot.humidity()).isEqualTo(55);
    }

    /** KmaWeatherClient 자체 버그(예: NPE)는 "기상청 API 실패"와 구분해 남긴다 — 둘 다 결측 처리는 같다 */
    @Test
    void 클라이언트의_예상치_못한_예외도_결측으로_처리한다() {
        given(client.getUltraSrtNcst(any(), any())).willThrow(new NullPointerException("버그"));
        WeatherService service = new WeatherService(client, new WeatherProperties("key"), CLOCK);

        assertThat(service.lookup(37.5665, 126.9780)).isNull();
    }
}
