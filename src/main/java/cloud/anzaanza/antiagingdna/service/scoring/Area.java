package cloud.anzaanza.antiagingdna.service.scoring;

import cloud.anzaanza.antiagingdna.config.ScoringProperties.Weights;
import java.math.BigDecimal;
import java.util.function.Function;

/**
 * 점수 영역 5종 (기획 [종합점수 산출 수식] §6).
 *
 * <p>가중치를 상수가 직접 꺼내온다. 영역마다 {@code if} 로 분기하면 영역이 늘 때마다 채점
 * 코드 전부를 고쳐야 하고, 한 군데를 빠뜨려도 컴파일은 통과한다.
 */
public enum Area {

    /** 신체 — 수면·식이·활동 */
    PHYSICAL(Weights::physical),

    /** 정신 — 수면·카페인·스트레스·규칙성 */
    MENTAL(Weights::mental),

    /** 감정 — 스트레스·기분 회복 */
    EMOTION(Weights::emotion),

    /** 사회 — 사람 만남 */
    SOCIAL(Weights::social),

    /**
     * 환경 — Phase 1 가중치 0. 온보딩·일지 모두 입력이 없어 기획 원안의 상수 50 은 모든
     * 사용자에게 동일하게 붙을 뿐이라 변별력이 없다 ({@code scoring.weights.environment=0}).
     */
    ENVIRONMENT(Weights::environment);

    private final Function<Weights, BigDecimal> weightAccessor;

    Area(Function<Weights, BigDecimal> weightAccessor) {
        this.weightAccessor = weightAccessor;
    }

    public BigDecimal weightIn(Weights weights) {
        return weightAccessor.apply(weights);
    }
}
