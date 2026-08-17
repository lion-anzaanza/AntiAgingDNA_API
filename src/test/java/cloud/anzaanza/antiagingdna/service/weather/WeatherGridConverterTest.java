package cloud.anzaanza.antiagingdna.service.weather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 기상청 활용가이드 부록 C 예제({@code docs/guide/}) 의 왕복 변환 값으로 검증한다 —
 * 격자 (59,125) ↔ 위경도 (37.488201, 126.929810).
 */
class WeatherGridConverterTest {

    @Test
    void 활용가이드_예제값으로_격자를_재현한다() {
        GridCoordinate grid = WeatherGridConverter.of(37.488201, 126.929810);

        assertThat(grid.nx()).isEqualTo(59);
        assertThat(grid.ny()).isEqualTo(125);
    }

    /**
     * 서울·부산 — 기상청 날씨누리(weather.go.kr) 가 실제로 쓰는 격자값과 대조
     * (예제 하나만으론 회귀를 못 잡아서 추가. 좌표는 각 시청 위경도).
     */
    @ParameterizedTest(name = "{0},{1} → ({2},{3})")
    @CsvSource({
        "37.5665, 126.9780, 60, 127", // 서울시청
        "35.1796, 129.0756, 98, 76" // 부산시청
    })
    void 주요_도시_격자값과_일치한다(double lat, double lon, int nx, int ny) {
        GridCoordinate grid = WeatherGridConverter.of(lat, lon);

        assertThat(grid.nx()).isEqualTo(nx);
        assertThat(grid.ny()).isEqualTo(ny);
    }
}
