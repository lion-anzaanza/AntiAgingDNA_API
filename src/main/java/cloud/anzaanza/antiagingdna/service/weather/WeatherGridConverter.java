package cloud.anzaanza.antiagingdna.service.weather;

/**
 * 위경도 → 기상청 격자(nx, ny) 변환 — Lambert Conformal Conic 투영.
 *
 * <p>기상청이 배포하는 활용가이드(《단기예보 조회서비스 Open API 활용가이드》 §2 부록,
 * {@code docs/guide/}) 안의 공식 C 예제({@code lamcproj})를 그대로 옮긴 것이다. 상수를
 * 지어내거나 추정하지 않았다 — 그 문서의 왕복 변환 예제(격자 (59,125) ↔ 위경도
 * (37.488201, 126.929810))로 이 구현을 검증했다({@code WeatherGridConverterTest}).
 */
public final class WeatherGridConverter {

    private static final double RE = 6371.00877; // 지구 반경 [km]
    private static final double GRID = 5.0; // 격자 간격 [km]
    private static final double SLAT1 = 30.0; // 표준위도 1 [degree]
    private static final double SLAT2 = 60.0; // 표준위도 2 [degree]
    private static final double OLON = 126.0; // 기준점 경도 [degree]
    private static final double OLAT = 38.0; // 기준점 위도 [degree]
    private static final double XO = 43; // 기준점 X좌표 [격자거리]
    private static final double YO = 136; // 기준점 Y좌표 [격자거리]
    private static final double DEGRAD = Math.PI / 180.0;

    private WeatherGridConverter() {}

    public static GridCoordinate of(double latitude, double longitude) {
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + latitude * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = longitude * DEGRAD - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int x = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int y = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new GridCoordinate(x, y);
    }
}
