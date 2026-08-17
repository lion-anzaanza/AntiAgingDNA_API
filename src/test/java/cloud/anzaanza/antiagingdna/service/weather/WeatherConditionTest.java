package cloud.anzaanza.antiagingdna.service.weather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** 기상청 활용가이드 §2 코드표 그대로 — 강수형태(PTY)가 없음(0)이 아니면 하늘상태(SKY)보다 우선한다. */
class WeatherConditionTest {

    @ParameterizedTest(name = "SKY={0}, PTY={1} → {2}")
    @CsvSource({
        "1, 0, CLEAR",
        "3, 0, MOSTLY_CLOUDY",
        "4, 0, OVERCAST",
        "1, 1, RAIN", // 하늘이 맑아도 강수형태가 있으면 강수가 우선
        "4, 2, RAIN_SNOW",
        "3, 3, SNOW",
        "1, 4, SHOWER",
        "4, 5, DRIZZLE",
        "3, 6, DRIZZLE_SNOW_FLURRY",
        "1, 7, SNOW_FLURRY"
    })
    void 코드값을_그대로_옮긴다(int sky, int pty, WeatherCondition expected) {
        assertThat(WeatherCondition.of(sky, pty)).isEqualTo(expected);
    }

    @Test
    void 둘_다_없으면_결측이다() {
        assertThat(WeatherCondition.of(null, null)).isNull();
    }

    @Test
    void 하늘상태만_없으면_강수형태로_판단한다() {
        assertThat(WeatherCondition.of(null, 1)).isEqualTo(WeatherCondition.RAIN);
    }

    @Test
    void 코드표에_없는_값은_지어내지_않고_결측으로_둔다() {
        assertThat(WeatherCondition.of(2, 0)).isNull(); // SKY=2 는 코드표에 없다
    }
}
