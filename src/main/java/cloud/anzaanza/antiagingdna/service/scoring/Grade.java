package cloud.anzaanza.antiagingdna.service.scoring;

/**
 * 점수(0~100)를 3단계로 나눈 등급 — 2026-08-17 결정. 경계값은
 * {@code scoring.grade.*} 설정(good-min=70, warn-min=40)을 따른다.
 *
 * <p>{@link GradeCalculator} 가 이 등급을 계산한다. 점수 자체를 바꾸지 않는 순수 표시값이라
 * {@code daily_score} 에 저장하지 않고 응답 직렬화 시점에 매번 계산한다 — 경계값이 바뀌어도
 * {@code scoring.version} 을 올리거나 과거 행을 재채점할 필요가 없다.
 */
public enum Grade {
    GOOD,
    WARN,
    DANGER
}
