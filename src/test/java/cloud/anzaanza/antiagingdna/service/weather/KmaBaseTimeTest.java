package cloud.anzaanza.antiagingdna.service.weather;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 기상청 활용가이드 §2 "예보 발표시각" 표({@code docs/guide/}) 기준 base_time 선택. */
class KmaBaseTimeTest {

    // ── 초단기실황 — 정시 생성, 10분 후 조회 가능 ────────────────

    @ParameterizedTest(name = "{0}시 {1}분 → base_time {2}")
    @CsvSource({
        "9, 9, 0800", // 아직 10분이 안 지나 09시 값이 없다 → 08시
        "9, 10, 0900", // 10분이 지나면 그 정시 값 사용
        "9, 39, 0900",
        "9, 59, 0900"
    })
    void 초단기실황_base_time(int hour, int minute, String expected) {
        KmaBaseTime base = KmaBaseTime.ultraShortTermObservation(LocalDateTime.of(2026, 8, 17, hour, minute));

        assertThat(base.baseTime()).isEqualTo(expected);
    }

    @Test
    void 초단기실황_자정_근처는_전날로_넘어간다() {
        KmaBaseTime base = KmaBaseTime.ultraShortTermObservation(LocalDateTime.of(2026, 8, 17, 0, 5));

        assertThat(base.baseDate()).isEqualTo("20260816");
        assertThat(base.baseTime()).isEqualTo("2300");
    }

    // ── 초단기예보 — 매시 30분 생성, 45분 후 조회 가능 ──────────────

    @ParameterizedTest(name = "{0}시 {1}분 → base_time {2}")
    @CsvSource({
        "9, 44, 0830", // 아직 09:30 값이 안 만들어졌다 → 08시 30분
        "9, 45, 0930",
        "9, 0, 0830"
    })
    void 초단기예보_base_time(int hour, int minute, String expected) {
        KmaBaseTime base = KmaBaseTime.ultraShortTermForecast(LocalDateTime.of(2026, 8, 17, hour, minute));

        assertThat(base.baseTime()).isEqualTo(expected);
    }

    @Test
    void 초단기예보_자정_근처는_전날로_넘어간다() {
        KmaBaseTime base = KmaBaseTime.ultraShortTermForecast(LocalDateTime.of(2026, 8, 17, 0, 20));

        assertThat(base.baseDate()).isEqualTo("20260816");
        assertThat(base.baseTime()).isEqualTo("2330");
    }
}
