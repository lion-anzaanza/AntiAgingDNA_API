package cloud.anzaanza.antiagingdna.service.weather;

import cloud.anzaanza.antiagingdna.config.WeatherProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 기상청_단기예보 조회서비스({@code VilageFcstInfoService_2.0}) HTTP 클라이언트.
 *
 * <p>초단기실황(getUltraSrtNcst)에는 하늘상태(SKY)가 없다 — 활용가이드 §2 코드표 기준으로
 * SKY 는 초단기예보·단기예보에만 있다. 그래서 기온·습도는 실황(관측값), 하늘상태·강수형태는
 * 예보(가장 가까운 미래 시각)에서 따로 가져온다.
 */
@Component
class KmaWeatherClient {

    private static final String BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";

    private final RestClient restClient;
    private final String apiKey;

    // 타임아웃은 spring.http.client.connect-timeout/read-timeout(application.properties) 로
    // 준다 — 여기서 builder 의 요청 팩토리를 직접 건드리면 MockRestServiceServer.bindTo(builder)
    // 가 테스트용으로 심어둔 팩토리를 덮어써서 테스트가 실제 네트워크로 나가버린다
    // (KmaWeatherClientTest 참고). 날씨 조회가 일지 저장 트랜잭션 안에서 일어나므로
    // (DiaryService.save) 타임아웃 자체는 반드시 필요하다 — 여기 대신 앱 전역 설정에 둔다.
    KmaWeatherClient(RestClient.Builder builder, WeatherProperties properties) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = properties.isConfigured() ? properties.decodedApiKey() : null;
    }

    /** 초단기실황 — 항목당 관측값이 하나뿐이라 {@code obsrValue} 를 그대로 쓴다 */
    Map<String, String> getUltraSrtNcst(KmaBaseTime base, GridCoordinate grid) {
        KmaResponse response = call("/getUltraSrtNcst", base, grid);
        Map<String, String> byCategory = new HashMap<>();
        for (KmaResponse.Item item : items(response)) {
            byCategory.putIfAbsent(item.category(), item.obsrValue());
        }
        return byCategory;
    }

    /** 초단기예보 — 한 항목이 6시간치를 담고 있어 시각이 가장 이른(=지금과 가장 가까운) 값을 쓴다 */
    Map<String, String> getUltraSrtFcst(KmaBaseTime base, GridCoordinate grid) {
        KmaResponse response = call("/getUltraSrtFcst", base, grid);
        Map<String, String> earliestKeyByCategory = new HashMap<>();
        Map<String, String> byCategory = new HashMap<>();
        for (KmaResponse.Item item : items(response)) {
            String timeKey = item.fcstDate() + item.fcstTime();
            String current = earliestKeyByCategory.get(item.category());
            if (current == null || timeKey.compareTo(current) < 0) {
                earliestKeyByCategory.put(item.category(), timeKey);
                byCategory.put(item.category(), item.fcstValue());
            }
        }
        return byCategory;
    }

    private List<KmaResponse.Item> items(KmaResponse response) {
        KmaResponse.Body body = response.response().body();
        if (body == null || body.items() == null) {
            return List.of();
        }
        return body.items().item();
    }

    private KmaResponse call(String path, KmaBaseTime base, GridCoordinate grid) {
        if (apiKey == null) {
            throw new WeatherLookupException("weather.api-key 가 설정되지 않았다");
        }
        KmaResponse response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("serviceKey", apiKey)
                        .queryParam("dataType", "JSON")
                        .queryParam("numOfRows", 1000)
                        .queryParam("pageNo", 1)
                        .queryParam("base_date", base.baseDate())
                        .queryParam("base_time", base.baseTime())
                        .queryParam("nx", grid.nx())
                        .queryParam("ny", grid.ny())
                        .build())
                .retrieve()
                .body(KmaResponse.class);

        if (response == null || response.response() == null) {
            throw new WeatherLookupException(path + " 응답이 비어있다");
        }
        String resultCode = response.response().header().resultCode();
        if (!"00".equals(resultCode)) {
            throw new WeatherLookupException(
                    path + " 실패: " + resultCode + " " + response.response().header().resultMsg());
        }
        return response;
    }
}
