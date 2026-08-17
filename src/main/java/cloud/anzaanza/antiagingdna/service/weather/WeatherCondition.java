package cloud.anzaanza.antiagingdna.service.weather;

/**
 * 하늘·강수 상태 — 기상청 활용가이드 §2 코드표({@code docs/guide/}) 그대로.
 *
 * <p>강수형태(PTY)가 "없음(0)"이 아니면 강수 종류가 하늘 상태보다 우선한다 — 비가 오는데
 * "맑음"으로 보일 수는 없다. 코드표에 없는 값은 지어내지 않고 {@code null}(결측)로 둔다.
 */
public enum WeatherCondition {
    /** SKY=1 */
    CLEAR,
    /** SKY=3 */
    MOSTLY_CLOUDY,
    /** SKY=4 */
    OVERCAST,
    /** PTY=1 */
    RAIN,
    /** PTY=2 */
    RAIN_SNOW,
    /** PTY=3 */
    SNOW,
    /** PTY=4 */
    SHOWER,
    /** PTY=5 */
    DRIZZLE,
    /** PTY=6 */
    DRIZZLE_SNOW_FLURRY,
    /** PTY=7 */
    SNOW_FLURRY;

    public static WeatherCondition of(Integer sky, Integer pty) {
        if (pty != null && pty != 0) {
            return switch (pty) {
                case 1 -> RAIN;
                case 2 -> RAIN_SNOW;
                case 3 -> SNOW;
                case 4 -> SHOWER;
                case 5 -> DRIZZLE;
                case 6 -> DRIZZLE_SNOW_FLURRY;
                case 7 -> SNOW_FLURRY;
                default -> null;
            };
        }
        if (sky == null) {
            return null;
        }
        return switch (sky) {
            case 1 -> CLEAR;
            case 3 -> MOSTLY_CLOUDY;
            case 4 -> OVERCAST;
            default -> null;
        };
    }
}
