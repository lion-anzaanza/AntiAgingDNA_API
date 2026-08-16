package cloud.anzaanza.antiagingdna.service.scoring;

/**
 * 홈 화면 오브 색상 7단계 — 2026-08-17 결정(FE backend-backlog.md #25).
 *
 * <p>이미 배포된 {@link Grade} 3단계(좋음/주의/위험, 경계 70/40)를 <b>깨지 않도록</b> 그 경계를
 * 하드 서브 경계로 삼아 안쪽을 세분화한다 — 같은 화면에 두 표시(오브 색·등급 배지)가 동시에
 * 나타나도 절대 서로 모순되지 않는다. 위험 2단계 · 주의 2단계 · 좋음 3단계.
 *
 * <p>{@link OrbStateCalculator} 가 계산한다. {@link Grade} 와 마찬가지로 표시 전용이라
 * 저장하지 않고 응답 직렬화 시점에 매번 계산한다.
 */
public enum OrbState {
    DANGER_LOW,
    DANGER_HIGH,
    WARN_LOW,
    WARN_HIGH,
    GOOD_LOW,
    GOOD_MID,
    GOOD_HIGH
}
