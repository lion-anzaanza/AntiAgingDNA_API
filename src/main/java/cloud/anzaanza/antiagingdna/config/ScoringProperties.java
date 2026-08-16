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
public record ScoringProperties(
        String version, Weights weights, Alpha alpha, int movingAverageDays, GradeThresholds grade) {

    /**
     * {@code scoring.grade.*} 가 하나도 없으면 {@link #grade} 가 통째로 {@code null} 로
     * 바인딩된다(중첩 레코드라 부분 바인딩이 안 된다) — 그 상태로 넘어가면
     * {@code GradeCalculator} 가 매 요청(점수·DNA 조회)마다 NPE 를 낸다. DB_URL·JWT_SECRET
     * 처럼 <b>부팅 시점에</b> 실패하게 한다.
     */
    public ScoringProperties {
        if (grade == null) {
            throw new IllegalStateException(
                    "scoring.grade.good-min / scoring.grade.warn-min 설정이 없다");
        }
    }

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

    /**
     * 점수(0~100) → 3단계 등급 경계값 — 2026-08-17 결정(FE backend-backlog.md #22,
     * {@code docs/PLANNING_OPEN_ITEMS.md} B-4). {@code goodMin} 이상은 좋음, {@code warnMin}
     * 이상 {@code goodMin} 미만은 주의, {@code warnMin} 미만은 위험.
     *
     * <p>점수 산출 자체를 바꾸지 않는 <b>표시 전용</b> 값이라 {@link #weights}/{@link #alpha} 와
     * 달리 바뀌어도 {@link #version} 을 올릴 필요가 없다 — 과거 점수와 섞여도 문제 될 원본이
     * 없다(등급은 저장하지 않고 매번 다시 계산한다).
     */
    public record GradeThresholds(BigDecimal goodMin, BigDecimal warnMin) {}
}
