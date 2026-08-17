package cloud.anzaanza.antiagingdna.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기상청 단기예보 조회서비스(공공데이터포털) 연동 설정 — FE backend-backlog.md #12.
 *
 * <p>{@code JWT_SECRET}/{@code DB_URL} 과 달리 <b>부팅을 막지 않는다</b> — 날씨는 일지의 부가
 * 정보라, 키가 없어도 앱은 정상 동작하고 {@link cloud.anzaanza.antiagingdna.service.weather.WeatherService}
 * 가 그냥 결측으로 처리한다.
 *
 * @param apiKey 공공데이터포털에서 발급받은 인증키. <b>반드시 data.go.kr 이 주는 "URL Encoding"
 *     형태</b>(예: {@code %2F}, {@code %3D} 포함)를 넣어야 한다 — {@link #decodedApiKey()} 가
 *     이걸 한 번 디코딩해서 돌려주고, HTTP 클라이언트가 그 결과를 다시 인코딩해 이중 인코딩을
 *     피한다. <b>이미 디코딩된("Decoding") 형태는 넣지 말 것</b> — {@code URLDecoder} 는 리터럴
 *     {@code +} 를 공백으로 바꾸는데, 디코딩 형태 키에 흔한 {@code +} 문자가 있으면 그 자리에서
 *     키가 깨지고, 이후 모든 호출이 인증 실패로 조용히 실패한다(WeatherService 가 결측 처리라
 *     로그만 남고 장애로 보이지 않는다).
 */
@ConfigurationProperties(prefix = "weather")
public record WeatherProperties(String apiKey) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String decodedApiKey() {
        return URLDecoder.decode(apiKey, StandardCharsets.UTF_8);
    }
}
