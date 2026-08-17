package cloud.anzaanza.antiagingdna.service.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cloud.anzaanza.antiagingdna.config.WeatherProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 실제 HTTP 요청 구성·JSON 파싱을 확인한다 — 특히 인증키 이중 인코딩(data.go.kr 이 이미
 * URL-encode 된 키를 주는데 클라이언트가 쿼리 파라미터를 또 인코딩하면 키가 깨진다).
 */
class KmaWeatherClientTest {

    /** data.go.kr 이 주는 "URL Encoding" 형태 — {@code /}, {@code =} 가 이미 퍼센트 인코딩돼 있다 */
    private static final String ENCODED_KEY = "abc%2Fdef%3D%3D";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void 인증키를_디코딩한_뒤_한_번만_인코딩해서_보낸다() {
        KmaWeatherClient client = new KmaWeatherClient(builder, new WeatherProperties(ENCODED_KEY));

        // '/' 는 쿼리 값에서 인코딩이 필수는 아니다(RFC 3986) — 디코딩 후 재인코딩한 값이
        // "abc/def%3D%3D" 로 나와도 "abc%2Fdef%3D%3D" 와 같은 문자열로 디코딩되므로 정상이다.
        // 여기서 확인하려는 건 "%25"(이중 인코딩)가 나오지 않는다는 것이다.
        server.expect(requestTo(
                        "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst"
                                + "?serviceKey=abc/def%3D%3D&dataType=JSON&numOfRows=1000&pageNo=1"
                                + "&base_date=20260817&base_time=0900&nx=60&ny=127"))
                .andRespond(withSuccess(
                        """
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                        "body":{"items":{"item":[
                          {"baseDate":"20260817","baseTime":"0900","category":"T1H","nx":60,"ny":127,"obsrValue":"28.3"},
                          {"baseDate":"20260817","baseTime":"0900","category":"REH","nx":60,"ny":127,"obsrValue":"55"}
                        ]}}}}
                        """,
                        MediaType.APPLICATION_JSON));

        Map<String, String> result =
                client.getUltraSrtNcst(new KmaBaseTime("20260817", "0900"), new GridCoordinate(60, 127));

        assertThat(result).containsEntry("T1H", "28.3").containsEntry("REH", "55");
        server.verify();
    }

    @Test
    void 예보는_가장_이른_시각의_값을_카테고리별로_고른다() {
        KmaWeatherClient client = new KmaWeatherClient(builder, new WeatherProperties(ENCODED_KEY));

        server.expect(requestTo(containsString("getUltraSrtFcst")))
                .andRespond(withSuccess(
                        """
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                        "body":{"items":{"item":[
                          {"baseDate":"20260817","baseTime":"0830","category":"SKY","fcstDate":"20260817","fcstTime":"1000","fcstValue":"4"},
                          {"baseDate":"20260817","baseTime":"0830","category":"SKY","fcstDate":"20260817","fcstTime":"0900","fcstValue":"1"},
                          {"baseDate":"20260817","baseTime":"0830","category":"PTY","fcstDate":"20260817","fcstTime":"0900","fcstValue":"0"}
                        ]}}}}
                        """,
                        MediaType.APPLICATION_JSON));

        Map<String, String> result =
                client.getUltraSrtFcst(new KmaBaseTime("20260817", "0830"), new GridCoordinate(60, 127));

        assertThat(result).containsEntry("SKY", "1").containsEntry("PTY", "0"); // 0900(더 이른 시각) 값
    }

    @Test
    void 응답코드가_정상이_아니면_예외를_던진다() {
        KmaWeatherClient client = new KmaWeatherClient(builder, new WeatherProperties(ENCODED_KEY));

        server.expect(requestTo(containsString("getUltraSrtNcst")))
                .andRespond(withSuccess(
                        """
                        {"response":{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":{"items":null}}}
                        """,
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getUltraSrtNcst(
                        new KmaBaseTime("20260817", "0900"), new GridCoordinate(60, 127)))
                .isInstanceOf(WeatherLookupException.class)
                .hasMessageContaining("03");
    }

    @Test
    void 키가_없으면_호출도_하지_않고_예외를_던진다() {
        KmaWeatherClient client = new KmaWeatherClient(builder, new WeatherProperties(""));

        assertThatThrownBy(() -> client.getUltraSrtNcst(
                        new KmaBaseTime("20260817", "0900"), new GridCoordinate(60, 127)))
                .isInstanceOf(WeatherLookupException.class);
        server.verify(); // 등록된 기대가 없으니 실제로 아무 요청도 없어야 통과한다
    }
}
