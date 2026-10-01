package cloud.anzaanza.antiagingdna.service.weather;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 날씨 조회는 일지 저장 트랜잭션 안에서 일어난다(DiaryService.save) — 외부 API 가 응답을 멈추면
 * 타임아웃만이 그 트랜잭션을 끊는다. 설정 키 이름이 부트 버전에 따라 바뀌면 아무 경고 없이
 * 무시되므로(FE backend-backlog.md #34 조사 중 발견), 실제 application.properties 의 값으로 응답 없는
 * 서버에 붙어 정말 끊기는지 확인한다.
 */
class HttpClientTimeoutConfigTest {

    @Test
    void application_properties_의_타임아웃이_자동구성된_RestClient_에_실제로_적용된다() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            // 연결은 받고 응답은 영원히 하지 않는다 — read-timeout 이 없으면 무한 대기한다
            List<Socket> accepted = new ArrayList<>();
            Thread acceptor = Thread.ofVirtual().start(() -> {
                try {
                    while (true) {
                        accepted.add(silent.accept());
                    }
                } catch (Exception ignored) {
                    // 서버 소켓이 닫히면 끝난다
                }
            });

            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            HttpClientAutoConfiguration.class,
                            ImperativeHttpClientAutoConfiguration.class,
                            RestClientAutoConfiguration.class))
                    .withPropertyValues(httpClientPropertiesFromApplicationProperties())
                    .run(context -> {
                        RestClient client = context.getBean(RestClient.Builder.class)
                                .baseUrl("http://127.0.0.1:" + silent.getLocalPort())
                                .build();

                        // application.properties 의 read-timeout(5s) 안에 끊겨야 한다. 여유를 두고 15s.
                        assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                                assertThatThrownBy(() -> client.get().uri("/").retrieve().toBodilessEntity())
                                        .isInstanceOf(ResourceAccessException.class));
                    });

            acceptor.interrupt();
            for (Socket socket : accepted) {
                socket.close();
            }
        }
    }

    private static String[] httpClientPropertiesFromApplicationProperties() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = HttpClientTimeoutConfigTest.class.getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        return properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith("spring.http."))
                .map(name -> name + "=" + properties.getProperty(name))
                .toArray(String[]::new);
    }
}
