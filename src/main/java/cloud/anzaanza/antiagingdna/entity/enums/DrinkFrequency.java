package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 음주 빈도 — 초기 진단 Q8. 근거: AUDIT-C 소비빈도 문항 (Bush et al., 1998).
 *
 * <p><b>임상 AUDIT-C 총점이 아니다</b> — 빈도 1문항만 차용한 것이다(기획 §7④). 신체 영역은
 * 수면·활동·식이 등 소스가 많아 음주는 보조 신호이므로, 마찰이 큰 풀버전(양·폭음)은 쓰지 않는다.
 *
 * <p>{@code NEVER} 가 "비음주" 상태를 겸하므로 별도의 {@code is_drinker} 플래그를 두지 않는다
 * — 두 컬럼을 두면 "비음주인데 주 4회"같은 모순 상태를 DB 가 허용하게 된다.
 */
@Getter
public enum DrinkFrequency implements ScoredOption {

    /** 전혀 안 마심 */
    NEVER(100),

    /** 월 1회 이하 */
    MONTHLY_OR_LESS(90),

    /** 월 2~4회 */
    TWO_TO_FOUR_PER_MONTH(70),

    /** 주 2~3회 */
    TWO_TO_THREE_PER_WEEK(45),

    /** 주 4회 이상 */
    FOUR_OR_MORE_PER_WEEK(20);

    private final int score;

    DrinkFrequency(int score) {
        this.score = score;
    }
}
