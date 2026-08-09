package cloud.anzaanza.antiagingdna.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 종합점수 산출 파라미터 — 영역 가중치와 baseline 결합 상수.
 *
 * <p>기획 §10 이 이 값들을 "미결정"으로 두었고 §7③ 이 Phase 2 에 분기별 재보정을 예고했다.
 * 즉 <b>바뀔 것을 전제한 값</b>이므로 코드 상수가 아니라 설정으로 뺀다.
 *
 * <p>재보정은 {@code application.properties} 의 값을 바꾸고 {@link #version} 을 올리는 것으로
 * 처리한다. 각 {@code daily_score} 행은 그때의 {@code version} 을 문자열로 기록하므로,
 * 과거 점수가 어떤 파라미터로 계산됐는지 추적할 수 있다.
 */
@ConfigurationProperties(prefix = "scoring")
public record ScoringProperties(String version, Weights weights, Alpha alpha, int movingAverageDays) {

    /**
     * 영역 가중치 W_c. 합이 1.000 이어야 한다 (기획 §6).
     *
     * <p>기획 원안은 신체 .30 / 정신 .25 / 감정 .20 / 사회 .15 / 환경 .10 이었으나, 환경이
     * 모든 사용자에게 동일한 상수라 변별력에 기여하지 않는다. 환경을 뺀 나머지 4개를 그대로
     * 재정규화(합 .90 으로 나눔)한 값이 기본값이다 — 원안의 <b>상대 비율은 보존</b>된다.
     */
    public record Weights(
            BigDecimal physical,
            BigDecimal mental,
            BigDecimal emotion,
            BigDecimal social,
            BigDecimal environment) {}

    /**
     * baseline ↔ 일지 결합 가중 {@code α(n) = n / (n + shrinkage)}, 상한 {@code cap}.
     * 기획 §7② 확정값 (shrinkage 7 · cap 0.95).
     */
    public record Alpha(int shrinkage, BigDecimal cap) {}
}
